package com.lockweb.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.lockweb.app.LockWebApplication
import com.lockweb.app.MainActivity
import com.lockweb.app.R
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lockweb.corefilter.AtomicBlockCounter
import com.lockweb.corefilter.BlockRule
import com.lockweb.corefilter.DnsDecision
import com.lockweb.corefilter.DnsFilter
import com.lockweb.corefilter.RuleMode
import com.lockweb.corefilter.RuleSet
import com.lockweb.corefilter.dns.DnsCache
import com.lockweb.corefilter.dns.DnsParser
import com.lockweb.corefilter.dns.DnsParseResult
import com.lockweb.corefilter.dns.DnsRCode
import com.lockweb.corefilter.packet.IpVersion
import com.lockweb.corefilter.packet.PacketCodec
import com.lockweb.corefilter.packet.ParsedUdpPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Android VpnService implementation for LockWeb.
 *
 * Implements DNS-only filtering by routing traffic destined for virtual DNS resolvers
 * (10.111.222.1 and fd00:1::1) into a local TUN interface. All non-DNS traffic bypasses
 * the VPN entirely, ensuring maximum performance, zero battery penalty, and no proxying.
 */
class LockWebVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.lockweb.app.vpn.START"
        const val ACTION_STOP = "com.lockweb.app.vpn.STOP"

        private const val NOTIFICATION_CHANNEL_ID = "lockweb_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        // Virtual network parameters
        const val VPN_IPV4_ADDRESS = "10.111.222.2"
        const val VPN_IPV4_DNS = "10.111.222.1"
        const val VPN_IPV6_ADDRESS = "fd00:1::2"
        const val VPN_IPV6_DNS = "fd00:1::1"
        const val VPN_MTU = 1500

        // Observable health and blocked counter
        private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Idle)
        val status: StateFlow<VpnStatus> = _status.asStateFlow()

        private val _blockedCount = MutableStateFlow(0L)
        val blockedCount: StateFlow<Long> = _blockedCount.asStateFlow()

        private val _isAlwaysOn = MutableStateFlow(false)
        val isAlwaysOn: StateFlow<Boolean> = _isAlwaysOn.asStateFlow()

        private val _isLockdownEnabled = MutableStateFlow(false)
        val isLockdownEnabled: StateFlow<Boolean> = _isLockdownEnabled.asStateFlow()

        private var activeInstance: LockWebVpnService? = null

        fun refreshAlwaysOnState(context: Context? = null) {
            val ctx = context ?: activeInstance?.applicationContext
            var alwaysOnFound = false
            var lockdownFound = false

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                activeInstance?.let { service ->
                    try {
                        alwaysOnFound = service.isAlwaysOn()
                        lockdownFound = service.isLockdownEnabled()
                    } catch (_: Exception) {}
                }
            }

            if (ctx != null && !alwaysOnFound) {
                try {
                    val alwaysOnApp = android.provider.Settings.Secure.getString(ctx.contentResolver, "always_on_vpn_app")
                    if (alwaysOnApp == ctx.packageName) {
                        alwaysOnFound = true
                        val lockdownVal = android.provider.Settings.Secure.getInt(ctx.contentResolver, "always_on_vpn_lockdown", 0)
                        if (lockdownVal == 1) {
                            lockdownFound = true
                        }
                    }
                } catch (_: Exception) {}
            }

            _isAlwaysOn.value = alwaysOnFound
            _isLockdownEnabled.value = lockdownFound
        }

        // Increment-only anonymous counter
        val blockCounter = AtomicBlockCounter(0L)

        // Bounded LRU DNS cache
        val cache = DnsCache(maxEntries = 1024)

        // Default initial rule set for Phase 3 testing (blocks reddit.com by default)
        private val initialRuleSet: RuleSet = RuleSet.compile(
            userRules = listOf(BlockRule("reddit.com", RuleMode.SUBDOMAINS)),
            blocklistDomains = emptyList()
        ).ruleSet

        // Atomic reference holding active compiled rules
        val ruleSetRef = AtomicReference<RuleSet>(initialRuleSet)

        /**
         * Sets status directly from UI or external components.
         */
        fun setStatus(newStatus: VpnStatus) {
            _status.value = newStatus
        }

        /**
         * Hot-swaps the active filtering rule set and flushes the DNS cache immediately.
         */
        fun updateRuleSet(newRuleSet: RuleSet) {
            ruleSetRef.set(newRuleSet)
            cache.clear()
        }

        /**
         * Resets the blocked query count.
         */
        fun resetCounter() {
            blockCounter.reset()
            _blockedCount.value = 0L
        }
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var upstreamResolver: UpstreamResolver? = null
    private var readerThread: Thread? = null
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isRunning = false
    @Volatile
    private var currentPrimaryDns = "1.1.1.1"
    @Volatile
    private var currentFallbackDns = "1.0.0.1"

    private val writeLock = Any()
    private val forwardLimiter = Semaphore(32)
    private val consecutiveFailures = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        refreshAlwaysOnState()
        createNotificationChannel()
        (application as? LockWebApplication)?.container?.let { container ->
            serviceScope.launch {
                container.ruleRepository.recompileAndPush()
            }
            serviceScope.launch {
                combine(
                    container.settingsRepository.primaryDns,
                    container.settingsRepository.fallbackDns
                ) { primary, fallback ->
                    Pair(primary, fallback)
                }.collect { (primary, fallback) ->
                    currentPrimaryDns = primary
                    currentFallbackDns = fallback
                    if (isRunning) {
                        upstreamResolver = UpstreamResolver(this@LockWebVpnService, primary, fallback)
                        cache.clear()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        refreshAlwaysOnState()
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, VpnService.SERVICE_INTERFACE -> {
                startVpn()
                return START_STICKY
            }
            else -> {
                // If started by system with any other action, ensure VPN runs
                startVpn()
                return START_STICKY
            }
        }
    }

    private fun startVpn() {
        if (isRunning || _status.value == VpnStatus.Active) {
            return
        }

        refreshAlwaysOnState(this)

        _status.value = VpnStatus.Starting

        // Start foreground service with specialUse type for VPN
        try {
            val notification = buildForegroundNotification()
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (e: Exception) {
            stopVpn("Failed to start foreground service: ${e.message}")
            stopSelf()
            return
        }

        // Establish the TUN interface
        val pfd = try {
            Builder()
                .setSession("LockWeb")
                .setMtu(VPN_MTU)
                .addAddress(VPN_IPV4_ADDRESS, 24)
                .addAddress(VPN_IPV6_ADDRESS, 64)
                .addDnsServer(VPN_IPV4_DNS)
                .addDnsServer(VPN_IPV6_DNS)
                .addRoute(VPN_IPV4_DNS, 32)
                .addRoute(VPN_IPV6_DNS, 128)
                .setBlocking(true)
                .establish()
        } catch (e: Exception) {
            stopVpn("Failed to configure TUN interface: ${e.message}")
            stopSelf()
            return
        }

        if (pfd == null) {
            stopVpn("TUN establish() returned null. Permission missing or replaced by another VPN.")
            stopSelf()
            return
        }

        tunInterface = pfd
        upstreamResolver = UpstreamResolver(this, currentPrimaryDns, currentFallbackDns)
        isRunning = true
        consecutiveFailures.set(0)

        // Perform internal filter self-test
        val selfTestPassed = runSelfTest()
        if (!selfTestPassed) {
            stopVpn("Filter self-test failed during startup.")
            stopSelf()
            return
        }

        // TUN is up and self-test passed
        _status.value = VpnStatus.Active

        // Now that the TUN session is officially active, query Always-on status via VpnService
        refreshAlwaysOnState(this)

        // Check upstream reachability asynchronously to detect offline or degraded state
        checkUpstreamReachability()

        // Launch packet read loop on a dedicated thread
        startPacketReader(pfd)
    }

    private fun startPacketReader(pfd: ParcelFileDescriptor) {
        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)

        readerThread = thread(name = "LockWeb-TunReader", isDaemon = true) {
            val packetBuffer = ByteArray(4096)

            try {
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    val bytesRead = inStream.read(packetBuffer)
                    if (bytesRead <= 0) break

                    val packetData = packetBuffer.copyOf(bytesRead)
                    handleInboundPacket(packetData, outStream)
                }
            } catch (_: IOException) {
                // Expected when TUN is closed during stopVpn()
            } catch (_: InterruptedException) {
                // Thread interrupted on shutdown
            } finally {
                try {
                    inStream.close()
                } catch (_: Exception) {}
                try {
                    outStream.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleInboundPacket(packetData: ByteArray, outStream: FileOutputStream) {
        val decision = DnsFilter.handlePacket(
            packetBytes = packetData,
            ruleSet = ruleSetRef.get(),
            cache = cache,
            counter = blockCounter
        )

        when (decision) {
            is DnsDecision.Block -> {
                // Query blocked: write NXDOMAIN reply to TUN immediately
                writeTunPacket(outStream, decision.replyPacket)
                _blockedCount.value = blockCounter.getCount()
            }
            is DnsDecision.CacheHit -> {
                // Query answered from cache: write reply packet to TUN
                writeTunPacket(outStream, decision.replyPacket)
            }
            is DnsDecision.Forward -> {
                // Query allowed: forward asynchronously so read loop is not blocked
                forwardQueryAsynchronously(decision, outStream)
            }
            is DnsDecision.Drop -> {
                // Malformed or non-DNS packet: safely discarded with zero logging
            }
        }
    }

    private fun forwardQueryAsynchronously(
        forwardDecision: DnsDecision.Forward,
        outStream: FileOutputStream
    ) {
        val resolver = upstreamResolver ?: return

        serviceScope.launch {
            try {
                forwardLimiter.withPermit {
                    val upstreamResponse = resolver.resolve(
                        forwardDecision.rawDnsPayload,
                        forwardDecision.requestPacket
                    )

                    // Check if response is valid for caching
                    val parsed = DnsParser.parse(upstreamResponse)
                    if (parsed is DnsParseResult.Success && parsed.message.header.rcode == DnsRCode.NOERROR) {
                        val q = parsed.message.question
                        if (q != null) {
                            cache.put(q.qname, q.qtype, q.qclass, upstreamResponse, 60L)
                        }
                        if (consecutiveFailures.getAndSet(0) > 0 && _status.value is VpnStatus.Degraded) {
                            _status.value = VpnStatus.Active
                        }
                    } else if (parsed is DnsParseResult.Success && parsed.message.header.rcode == DnsRCode.SERVFAIL) {
                        val failures = consecutiveFailures.incrementAndGet()
                        if (failures >= 3 && _status.value == VpnStatus.Active) {
                            _status.value = VpnStatus.Degraded("Upstream DNS returned SERVFAIL")
                        }
                    }

                    val replyPacket = PacketCodec.buildReplyPacket(
                        forwardDecision.requestPacket,
                        upstreamResponse
                    )
                    writeTunPacket(outStream, replyPacket)
                }
            } catch (_: Exception) {
                val failures = consecutiveFailures.incrementAndGet()
                if (failures >= 3 && _status.value == VpnStatus.Active) {
                    _status.value = VpnStatus.Degraded("Upstream DNS query failed")
                }
            }
        }
    }

    private fun writeTunPacket(outStream: FileOutputStream, packet: ByteArray) {
        synchronized(writeLock) {
            try {
                outStream.write(packet)
                outStream.flush()
            } catch (_: IOException) {
                // Socket or file descriptor closed
            }
        }
    }

    /**
     * Executes an internal synthetic self-test to verify DnsFilter and PacketCodec.
     */
    private fun runSelfTest(): Boolean {
        return try {
            val syntheticQuery = createSyntheticQuery("selftest.lockweb.internal")
            val parseResult = DnsParser.parse(syntheticQuery)
            if (parseResult !is DnsParseResult.Success) {
                return false
            }

            // Note: buildReplyPacket swaps src & dst. To generate an inbound packet with dstPort=53,
            // we provide srcPort=53 and dstPort=54321 in the template request:
            val syntheticPacket = PacketCodec.buildReplyPacket(
                ParsedUdpPacket(
                    version = IpVersion.IPV4,
                    srcIp = byteArrayOf(10, 111, 222.toByte(), 1),
                    dstIp = byteArrayOf(10, 111, 222.toByte(), 2),
                    srcPort = 53,
                    dstPort = 54321,
                    payload = byteArrayOf(),
                    ipHeaderLength = 20
                ),
                syntheticQuery
            )

            val decision = DnsFilter.handlePacket(
                syntheticPacket,
                ruleSetRef.get(),
                cache,
                null
            )
            decision !is DnsDecision.Drop
        } catch (_: Exception) {
            false
        }
    }

    private fun checkUpstreamReachability() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val resolver = upstreamResolver ?: return@withContext
                    val pingQuery = createSyntheticQuery("one.one.one.one")
                    val fakePacket = ParsedUdpPacket(
                        version = IpVersion.IPV4,
                        srcIp = byteArrayOf(10, 111, 222.toByte(), 2),
                        dstIp = byteArrayOf(1, 1, 1, 1),
                        srcPort = 54321,
                        dstPort = 53,
                        payload = pingQuery,
                        ipHeaderLength = 20
                    )
                    val response = resolver.resolve(pingQuery, fakePacket)
                    if (response.isEmpty() || (response.size >= 4 && (response[3].toInt() and 0x0F) == DnsRCode.SERVFAIL)) {
                        if (_status.value == VpnStatus.Active) {
                            _status.value = VpnStatus.Degraded("Upstream DNS initial check unreachable")
                        }
                    }
                } catch (_: Exception) {
                    if (_status.value == VpnStatus.Active) {
                        _status.value = VpnStatus.Degraded("Upstream DNS unreachable")
                    }
                }
            }
        }
    }

    private fun createSyntheticQuery(domain: String, queryId: Int = 0x1A2B): ByteArray {
        val labels = domain.split(".")
        val qnameOut = ByteArrayOutputStream()
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            qnameOut.write(bytes.size)
            qnameOut.write(bytes)
        }
        qnameOut.write(0)
        val qnameBytes = qnameOut.toByteArray()

        val buffer = ByteArray(12 + qnameBytes.size + 4)
        buffer[0] = ((queryId shr 8) and 0xFF).toByte()
        buffer[1] = (queryId and 0xFF).toByte()
        buffer[2] = 0x01.toByte() // Standard query with RD=1
        buffer[3] = 0x00.toByte()
        buffer[4] = 0x00.toByte()
        buffer[5] = 0x01.toByte() // QDCOUNT = 1

        System.arraycopy(qnameBytes, 0, buffer, 12, qnameBytes.size)
        val offset = 12 + qnameBytes.size
        buffer[offset] = 0x00.toByte()
        buffer[offset + 1] = 0x01.toByte() // QTYPE = A (1)
        buffer[offset + 2] = 0x00.toByte()
        buffer[offset + 3] = 0x01.toByte() // QCLASS = IN (1)

        return buffer
    }

    private fun stopVpn(errorReason: String? = null) {
        isRunning = false

        readerThread?.interrupt()
        readerThread = null

        try {
            tunInterface?.close()
        } catch (_: Exception) {}
        tunInterface = null

        serviceScope.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}

        if (errorReason != null) {
            _status.value = VpnStatus.Error(errorReason)
        } else if (_status.value !is VpnStatus.Error) {
            _status.value = VpnStatus.Idle
        }
    }

    override fun onRevoke() {
        stopVpn()
        _status.value = VpnStatus.Degraded("VPN revoked by Android or another VPN app")
        postRevocationNotification()
        super.onRevoke()
    }

    private fun postRevocationNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_revoked_title))
            .setContentText(getString(R.string.vpn_revoked_text))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(1002, notification)
        } catch (_: SecurityException) {}
    }

    override fun onDestroy() {
        stopVpn()
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "LockWeb Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing notification required for LockWeb VPN DNS filtering"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("LockWeb Protection Active")
            .setContentText("DNS filtering is actively protecting this device.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
