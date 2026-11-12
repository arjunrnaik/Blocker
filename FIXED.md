# 🔍 LockWeb — Complete Code Audit & Fix Plan

> [!IMPORTANT]
> This document lists **every problem found** across the entire LockWeb codebase — security holes, bugs, half-baked logic, performance issues, data risks, and future-proofing gaps. Each entry includes the optimal solution.

---

## Severity Legend

| Severity | Meaning |
|----------|---------|
| 🔴 CRITICAL | Security breach, data loss, or bypass that must be fixed immediately |
| 🟠 HIGH | Significant bug or security risk that will cause real user-facing problems |
| 🟡 MEDIUM | Correctness issue, half-baked feature, or future risk that should be addressed |
| 🔵 LOW | Code quality, minor edge case, or hardening improvement |

---

## 🔴 CRITICAL Issues

### C-1: Debug Keystore Committed to Public Repository
**File:** `app/debug.keystore`  
**Category:** Security — Credential Leak  
**Problem:** The debug signing keystore (`debug.keystore`) is checked into a **public** GitHub repository. While this is "only" a debug keystore, it means anyone can build APKs with the exact same signing identity, enabling malicious APK substitution attacks on debug-signed builds.  
**Impact:** An attacker can create a trojanized APK that Android treats as an "update" to the debug build, silently replacing it.  
**Solution:**
1. Add `app/debug.keystore` to `.gitignore`
2. Run `git rm --cached app/debug.keystore` to remove it from tracking
3. Remove the `signingConfigs { debug { ... } }` block from `app/build.gradle.kts` — Android Studio auto-generates a per-machine debug keystore
4. For CI builds, generate or inject the keystore via GitHub Secrets

---

### C-2: Release Build Has Code Minification Disabled
**File:** `app/build.gradle.kts` — line 36  
**Category:** Security — Reverse Engineering  
**Problem:** `isMinifyEnabled = false` in the `release` build type. R8/ProGuard is completely disabled for release APKs.  
**Impact:**
- Entire source code (class names, method names, string constants) is trivially decompilable
- KeyStore alias `"LockWebMasterCredentialKey"` is visible in plain text
- All guard rules, package names, class patterns are exposed — an attacker can study and craft bypasses
- APK size is unnecessarily large  
**Solution:**
```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```
And add necessary ProGuard keep rules (see C-3).

---

### C-3: ProGuard Rules File Is Empty
**File:** `app/proguard-rules.pro`  
**Category:** Build — Missing Configuration  
**Problem:** Only contains a comment. When R8 is enabled (fixing C-2), Room entities, DAO interfaces, DataStore serialization, and WorkManager workers will be stripped or renamed, causing runtime crashes.  
**Solution:** Add proper keep rules:
```proguard
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# LockWeb security models (serialized to bytes)
-keep class com.lockweb.app.security.PasswordHasher$HashRecord { *; }

# Device Admin Receiver (referenced by XML)
-keep class com.lockweb.app.admin.LockWebDeviceAdminReceiver { *; }

# Accessibility Service (referenced by XML)
-keep class com.lockweb.app.guard.AccessibilityGuardService { *; }
```

---

### C-4: Credential Files Stored as Plaintext on Filesystem
**File:** `AuthManager.kt` — `FileCredentialStorage` (lines 17-52)  
**Category:** Security — Data at Rest  
**Problem:** `cred_pwd.bin` and `cred_rec.bin` are stored in `context.filesDir` as regular files. While the content is encrypted by Android KeyStore AES-GCM, the **files themselves** are readable by any process running as the app's UID. On rooted devices, this means the encrypted blobs can be extracted. More critically, `context.filesDir` is included in **ADB backup** (though `allowBackup=false` is set in the manifest, which helps).  
**Impact:** On a rooted device, attacker extracts encrypted blobs + if they can compromise the KeyStore key (hardware-backed KeyStore makes this very hard, but software KeyStore on emulators is trivially extractable), they get the master password hash.  
**Solution:**
1. Add `setUserAuthenticationRequired(true)` to `KeyGenParameterSpec.Builder` for hardware-backed auth binding (optional — may cause UX complexity)
2. Set file permissions explicitly: `pwdFile.setReadable(false, false); pwdFile.setReadable(true, true)` 
3. Consider using `EncryptedFile` from AndroidX Security library as a defense-in-depth layer

---

### C-5: Rate Limiter State File Is Tamperable on Rooted Devices
**File:** `RateLimiter.kt` — `FileRateLimitStore` (lines 32-71)  
**Category:** Security — Bypass  
**Problem:** `rate_limit_state.txt` is a plain text file in `filesDir` containing the failure count and lockout timestamp. On a rooted device, an attacker can simply delete this file to reset the rate limiter and resume brute-force attempts against the master password.  
**Impact:** Complete bypass of brute-force protection on rooted devices.  
**Solution:**
1. Encrypt the rate limit state file with the KeyStore vault (same as credentials)
2. Store a hash/checksum to detect tampering
3. Additionally, embed a secondary in-KeyStore counter using `KeyGenParameterSpec.Builder.setMaxUsageCount()` or store the attempt count inside the encrypted credential blob itself

---

### C-6: Room Database Is Not Encrypted
**File:** `AppDatabase.kt` (lines 25-29)  
**Category:** Security — Data at Rest  
**Problem:** The SQLite database `lockweb.db` is unencrypted. While it only contains blocking rules (not credentials), on a rooted device an attacker can read the database to:
1. See exactly what domains the user is blocking (privacy leak)
2. Modify the database to remove blocking rules, bypassing protection  
**Impact:** Privacy breach (blocked domain list visible) and protection bypass (rules can be edited directly).  
**Solution:** Use SQLCipher for Android:
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
    .openHelperFactory(SupportFactory(getOrCreateDbKey()))
    .build()
```

---

### C-7: DataStore Preferences Are Not Encrypted
**File:** `SettingsRepository.kt` — line 16  
**Category:** Security — Data at Rest  
**Problem:** `lockweb_settings` DataStore is unencrypted. On a rooted device, an attacker can edit the preferences XML to:
- Set `adult_blocklist_enabled` to `false` — **completely bypassing the "permanent" adult filter**
- Set `onboarding_completed` to `false` to trigger re-onboarding  
**Impact:** The core self-control mandate ("once enabled, cannot be disabled") is trivially defeated on rooted devices.  
**Solution:** Use `EncryptedSharedPreferences` or the Tink-backed `EncryptedDataStore` from AndroidX Security:
```kotlin
val Context.settingsDataStore by preferencesDataStore(
    name = "lockweb_settings",
    produceMigrations = { context -> listOf() },
    // Use EncryptedPreferencesDataStore wrapper
)
```
Or store the adult blocklist flag inside the KeyStore-encrypted credential blob so it's tamper-proof.

---

## 🟠 HIGH Issues

### H-1: `password.toCharArray()` Leak — Password String Stays in Memory
**File:** `AuthManager.kt` — line 80, `OnboardingWizardScreen.kt` — line 229  
**Category:** Security — Memory  
**Problem:** `verifyPassword(password: String)` on line 80 converts to `CharArray` via `password.toCharArray()`. The original `String` object is **immutable** and stays in JVM heap until GC collects it. The `CharArray` is properly cleared by `PBEKeySpec.clearPassword()`, but the `String` remains.  
**Impact:** A memory dump or Frida hook could extract the plaintext password.  
**Solution:**
1. Change the API to accept `CharArray` directly: `fun verifyPassword(password: CharArray): AuthResult`
2. In Compose, store password state as `CharArray` instead of `String`, or at minimum, overwrite the old `String`'s backing char array via reflection (fragile) or accept the risk as inherent to Compose text fields

---

### H-2: `SYSTEM_ALERT_WINDOW` Permission Requested Unnecessarily
**File:** `AndroidManifest.xml` — line 9  
**Category:** Security — Over-Permission  
**Problem:** `SYSTEM_ALERT_WINDOW` is declared but never used in the codebase. No overlay windows are drawn.  
**Impact:** 
- Play Store review risk (this permission triggers manual review)
- Attackers could leverage this permission if the app were compromised
- Users may be alarmed by the permission request  
**Solution:** Remove `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />` from the manifest.

---

### H-3: DNS Settings Change Does Not Restart VPN / Update UpstreamResolver
**File:** `SettingsScreen.kt` — lines 334-337, `LockWebVpnService.kt`  
**Category:** Bug — Half-Baked Feature  
**Problem:** When the user changes the upstream DNS resolver (e.g., from Cloudflare to Google), `settingsRepository.setUpstreamDns()` is called, but:
1. `UpstreamResolver` is created once in `startVpn()` with hardcoded `primaryDns = "1.1.1.1"` and `fallbackDns = "1.0.0.1"`
2. No code reads the stored DNS settings to construct the resolver
3. No code restarts the VPN or hot-swaps the resolver when DNS settings change  
**Impact:** The DNS change UI is completely non-functional. Users think they've changed their DNS but traffic still goes to Cloudflare.  
**Solution:**
1. In `startVpn()`, read DNS settings from `SettingsRepository` before creating `UpstreamResolver`
2. Either restart the VPN when DNS settings change, or make `UpstreamResolver` read DNS settings dynamically
3. Show a "VPN restart required" toast after DNS change

---

### H-4: `OnboardingWizardScreen` Does Not Have FLAG_SECURE
**File:** `OnboardingWizardScreen.kt`  
**Category:** Security — Screenshot / Screen Recording  
**Problem:** The onboarding wizard displays the recovery code in plaintext on Step 3. This screen does NOT have `FLAG_SECURE`, meaning:
1. Screenshots can capture the recovery code
2. Screen recording apps can record it
3. The recovery code may appear in the Recents/task switcher thumbnail  
**Impact:** Recovery code leak via screenshot or screen capture.  
**Solution:** The `OnboardingWizardScreen` is rendered inside `MainActivity`, which doesn't set `FLAG_SECURE`. Add `FLAG_SECURE` to `MainActivity.onCreate()` when in the onboarding flow, or wrap the recovery code display in a `DisposableEffect` that sets/unsets the flag:
```kotlin
val window = (context as? Activity)?.window
DisposableEffect(Unit) {
    window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
}
```

---

### H-5: Password Change Feature Is Not Implemented
**File:** `SettingsScreen.kt` — line 61, line 279  
**Category:** Half-Baked Feature  
**Problem:** `showPasswordChangeDialog` state exists (line 61) and is triggered (line 279: `"change_password" -> showPasswordChangeDialog = true`), but there is **no dialog rendered** when `showPasswordChangeDialog` is `true`. The code simply sets the variable and nothing happens.  
**Impact:** Users who want to change their master password cannot do so. The button appears to do nothing after authentication.  
**Solution:** Implement the password change dialog:
```kotlin
if (showPasswordChangeDialog) {
    PasswordChangeDialog(
        authManager = app.container.authManager,
        onPasswordChanged = { newRecoveryCode ->
            // Show new recovery code
            showPasswordChangeDialog = false
        },
        onDismiss = { showPasswordChangeDialog = false }
    )
}
```

---

### H-6: No Database Migration Strategy
**File:** `AppDatabase.kt` — line 11  
**Category:** Data Loss  
**Problem:** `exportSchema = false` and no `Migration` objects are defined. When the database schema changes in a future version (e.g., adding a new column), the app will crash with `IllegalStateException` or silently destroy all user data if `fallbackToDestructiveMigration()` is added.  
**Impact:** Any future schema change will either crash the app or destroy all user-added blocking rules.  
**Solution:**
1. Set `exportSchema = true` to generate JSON schemas for each version
2. Define migration paths: `addMigrations(Migration_1_2, Migration_2_3, ...)`
3. Add this to `.gitignore` for the schemas directory: `app/schemas/`

---

### H-7: `AuthGateDialog` Missing FLAG_SECURE — Password Visible in Screenshots
**File:** `AuthGateDialog.kt`  
**Category:** Security — Screenshot Leak  
**Problem:** `AuthGateDialog` is an `AlertDialog` composable that shows a password input field. Unlike `LockWebPasswordGateActivity` (which correctly sets `FLAG_SECURE`), this dialog runs inside `MainActivity` or `SettingsScreen` which do NOT have `FLAG_SECURE`.  
**Impact:** Screenshots and screen recordings can capture the password field content.  
**Solution:** Add `FLAG_SECURE` to the containing Activity window when the dialog is shown:
```kotlin
val window = (LocalContext.current as? Activity)?.window
DisposableEffect(Unit) {
    window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
}
```

---

### H-8: Adding Block Rules Does Not Require Authentication
**File:** `DashboardScreen.kt` — lines 320-332, `BlocklistScreen.kt` — lines 297-308  
**Category:** Security — Missing Auth Gate  
**Problem:** Adding new blocking rules via the `AddRuleDialog` does NOT require entering the master password. Only **deleting** rules requires authentication.  
**Impact:** While adding rules doesn't weaken protection (it strengthens it), this is inconsistent. More importantly, a malicious person with temporary physical access could add thousands of rules to make the device unusable (blocking every website), effectively performing a DoS on the device owner.  
**Solution:** Either:
1. Add an `AuthGateDialog` before `AddRuleDialog` (consistent with delete)
2. Or accept this as intentional (adding rules = more protection = doesn't need auth) but add a max rule count (e.g., 10,000) to prevent DoS

---

### H-9: `GuardSessionManager` Uses Singleton with Hardcoded Clock — Not Process-Safe
**File:** `GuardSessionManager.kt` — line 18  
**Category:** Bug — Architecture  
**Problem:** `GuardSessionManager.instance` is a Kotlin `companion object` singleton. The `AccessibilityGuardService` runs in the **same process** as the app by default (no `android:process` attribute), so this works. However, if the app process is killed and only the Accessibility Service survives (which Android can keep alive independently), the singleton state (`authorizedUntilMillis`) is lost. Additionally, `@Volatile` provides visibility but not atomicity across potential multi-thread access to `grantTemporaryAccess()` and `isTemporarilyAuthorized()`.  
**Impact:** After process death + restart, the guard won't remember temporary authorization, which is actually *safe* (it defaults to blocking). But if the app process is killed while the user has a temporary authorization window, they could lose access mid-session.  
**Solution:** This is mostly acceptable as-is (fails safe), but document it. For robustness, persist the authorization timestamp to a file or SharedPreferences so it survives process death.

---

## 🟡 MEDIUM Issues

### M-1: VPN Service Not Restarted After Blocklist Update
**File:** `OisdBlocklistUpdateWorker.kt`, `RuleRepository.kt`  
**Category:** Bug — Half-Baked  
**Problem:** When `OisdBlocklistUpdateWorker` downloads a new blocklist, it calls `manager.atomicUpdate()` and `manager.loadDomains()` but does NOT trigger `ruleRepository.recompileAndPush()`. The `RuleRepository.init {}` block observes `blockRuleDao.getAllRules()` and `isAdultBlocklistEnabled` — but **not the blocklist file itself**. So a new download doesn't propagate to the VPN's active `RuleSet` until the next app restart.  
**Impact:** Weekly blocklist updates don't take effect until the user opens the app or reboots.  
**Solution:** After `settingsRepo.recordBlocklistUpdate()`, trigger `RuleRepository.recompileAndPush()`:
```kotlin
// In OisdBlocklistUpdateWorker.doWork():
val app = applicationContext as LockWebApplication
app.container.ruleRepository.recompileAndPush()
```

---

### M-2: `UpstreamResolver` Creates New Socket Per Query — No Connection Reuse
**File:** `UpstreamResolver.kt` — lines 84-110  
**Category:** Performance  
**Problem:** Every DNS query creates a new `DatagramSocket`, sends one packet, receives one response, and closes. This involves repeated system calls for socket creation, `protect()`, and teardown.  
**Impact:** Higher latency and CPU usage under load. On devices with many apps making DNS queries simultaneously, this causes unnecessary overhead.  
**Solution:** Maintain a pool of 2-4 pre-created, protected UDP sockets. Reuse them with proper synchronization. Fall back to new sockets if pool is exhausted.

---

### M-3: `loadDomains()` Reads 100K+ Domains Into Memory Every Recompile
**File:** `BlocklistManager.kt` — lines 104-111, `RuleRepository.kt` — lines 95-112  
**Category:** Performance — Memory  
**Problem:** Every time rules change (user adds/removes a rule), `compileAndPush()` calls `blocklistManager.loadDomains()` which reads the entire OISD blocklist file (~100,000+ domains) into a `List<String>`. Combined with the `RuleSet.compile()` creating a `Map<String, RuleMode>`, this causes:
1. ~10-20MB of transient memory allocation per recompile
2. Noticeable GC pressure  
**Impact:** Brief UI stutter and memory spike when adding/deleting rules.  
**Solution:**
1. Cache the parsed blocklist domains in `BlocklistManager` after first load
2. Invalidate cache only when the file changes (after `atomicUpdate()`)
3. Use a `HashSet<String>` for faster lookup instead of `List<String>`

---

### M-4: Recovery Code Cannot Be Regenerated Without Full Password Reset
**File:** `AuthManager.kt`, `SettingsScreen.kt`  
**Category:** Half-Baked Feature  
**Problem:** If a user loses their recovery code but still knows their password, there's no way to generate a new recovery code. The only way is through `resetPasswordWithRecovery()` which requires the OLD recovery code.  
**Impact:** User who loses recovery code has reduced safety net. If they forget their password later, factory reset is the only option.  
**Solution:** Add a `regenerateRecoveryCode(password: CharArray): String?` method to `AuthManager` that:
1. Verifies the current password
2. Generates a new recovery code
3. Encrypts and stores it
4. Returns the new code for display

---

### M-5: DNS Cache Uses Fixed 60-Second TTL Instead of Actual Record TTL
**File:** `LockWebVpnService.kt` — line 349  
**Category:** Bug — Correctness  
**Problem:** `cache.put(q.qname, q.qtype, q.qclass, upstreamResponse, 60L)` hardcodes a 60-second TTL. The actual TTL from the DNS response's answer section is ignored.  
**Impact:**
- Short-lived DNS records (e.g., CDN load balancers with 5s TTL) become stale
- Long-lived records (TTL 86400) are re-fetched every 60 seconds, wasting bandwidth  
**Solution:** Parse the minimum TTL from the response's answer records:
```kotlin
val ttl = DnsParser.extractMinTtl(upstreamResponse) ?: 60L
cache.put(q.qname, q.qtype, q.qclass, upstreamResponse, ttl.coerceIn(5L, 3600L))
```

---

### M-6: `BootReceiver` May Fail Silently on Android 14+ Due to Background Start Restrictions
**File:** `BootReceiver.kt` — lines 46-52  
**Category:** Bug — Compatibility  
**Problem:** On Android 14 (API 34+), starting a foreground service from a `BroadcastReceiver` requires the `FOREGROUND_SERVICE_SPECIAL_USE` permission (which is declared) **and** the system must allow the start. However, Android 14 added new restrictions: the app must have been in the foreground recently or have a specific exemption. After a cold boot, the app has never been in the foreground.  
**Impact:** VPN may not auto-restart after reboot on Android 14+, leaving the user unprotected until they manually open the app.  
**Solution:** 
1. The current fallback to `postResumeNotification()` is the correct approach but could be more prominent
2. Use `AlarmManager.setExactAndAllowWhileIdle()` as an additional retry after 30 seconds
3. Test thoroughly on API 34+ physical devices

---

### M-7: Accessibility Guard Does Not Cover OEM Uninstaller Apps
**File:** `GuardedTargets.kt`  
**Category:** Incomplete — Half-Baked  
**Problem:** Guard rules exist for AOSP Settings, Samsung, Xiaomi, Play Store, and Chrome. Missing coverage for:
- **OnePlus** (`com.oneplus.security`)
- **Oppo/Realme** (`com.coloros.safecenter`)
- **Vivo** (`com.vivo.abe`, `com.bbk.launcher2`)
- **Huawei** (`com.huawei.systemmanager`)
- **Package Installer** (`com.google.android.packageinstaller` — the uninstall confirmation dialog)
- **Files apps** (`com.google.android.apps.nbu.files`) that can trigger uninstall  
**Impact:** On non-Samsung/Xiaomi OEM devices, users can access Force Stop, Clear Data, or uninstall without the password gate.  
**Solution:** Add GuardRules for the package installer and major OEMs:
```kotlin
GuardRule(
    id = "package_installer",
    description = "Android Package Installer / Uninstall Dialog",
    targetPackage = "com.google.android.packageinstaller",
    classPatterns = listOf(Regex(".*Uninstall.*", RegexOption.IGNORE_CASE))
),
// ... more OEM rules
```

---

### M-8: `RuleRepository` Coroutine Scope Leaks
**File:** `RuleRepository.kt` — line 33  
**Category:** Memory Leak  
**Problem:** `private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)` is created in the constructor but never cancelled. Since `RuleRepository` lives for the lifetime of `AppContainer` (which lives for the lifetime of the Application), this is technically acceptable. However, the `init {}` block launches a `launchIn(scope)` collect that runs forever.  
**Impact:** Not a practical memory leak since it matches Application lifecycle, but it's not cleanly structured.  
**Solution:** Accept as-is (Application-scoped), but add a comment: `// Application-scoped: intentionally never cancelled`. Or use `ProcessLifecycleOwner.get().lifecycleScope` for clarity.

---

### M-9: `OnboardingWizardScreen` Recovery Code May Be Lost on Config Change
**File:** `OnboardingWizardScreen.kt` — line 83  
**Category:** Bug — State Loss  
**Problem:** `var recoveryCode by remember { mutableStateOf("") }` — the recovery code is stored in Compose `remember` state. If the device rotates or the Activity is recreated (configuration change), `remember` state is lost. The recovery code is generated in Step 2 and displayed in Step 3. If the user rotates the device on Step 3, the code disappears and they can never see it again.  
**Impact:** User permanently loses their recovery code without knowing it.  
**Solution:** Use `rememberSaveable` instead of `remember` for the recovery code:
```kotlin
var recoveryCode by rememberSaveable { mutableStateOf("") }
```

---

### M-10: `version` Hardcoded as "1.0.0" in Settings Screen
**File:** `SettingsScreen.kt` — line 255  
**Category:** Maintenance — Half-Baked  
**Problem:** `Text(text = "LockWeb v1.0.0")` is hardcoded. When the version changes in `build.gradle.kts`, this string won't update.  
**Impact:** Misleading version display to users.  
**Solution:** Read from `BuildConfig.VERSION_NAME`:
```kotlin
Text(text = "LockWeb v${BuildConfig.VERSION_NAME}")
```

---

### M-11: `DnsCache` Key Uses `qname.lowercase()` but `RuleSet` Uses `IDN.toASCII()` — Inconsistent Normalization
**File:** `DnsCache.kt` — line 46, `RuleSet.kt` — line 54-58  
**Category:** Bug — Correctness  
**Problem:** Cache keys use `qname.lowercase()` directly, while `RuleSet.matches()` applies `IDN.toASCII()` Punycode conversion. If an internationalized domain (IDN) is queried, the cache may store it with one key form while the filter checks a different form.  
**Impact:** Edge case: IDN domains might bypass the cache or the filter depending on which path normalizes first.  
**Solution:** Apply `IDN.toASCII()` normalization in `DnsCache.get()` and `DnsCache.put()` as well.

---

### M-12: `FileRateLimitStore` Reads File Twice Per Check
**File:** `RateLimiter.kt` — `FileRateLimitStore` (lines 39-57)  
**Category:** Performance / Bug  
**Problem:** `getFailedAttempts()` reads and parses the file. `getLockedUntilMillis()` reads and parses the **same file again**. `RateLimiter.checkStatus()` calls both, resulting in 2 file reads. `recordFailure()` calls `getFailedAttempts()` (1 read) then `saveState()` (1 write). This is inefficient and introduces a TOCTOU race: the file could change between the two reads.  
**Impact:** Minor performance issue and theoretical race condition. On a non-rooted device, this is practically safe since only the app writes the file.  
**Solution:** Read the file once and parse both values:
```kotlin
private fun readState(): Pair<Int, Long> {
    if (!file.exists()) return Pair(0, 0L)
    return try {
        val lines = file.readLines()
        val attempts = lines.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val lockedUntil = lines.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
        Pair(attempts, lockedUntil)
    } catch (_: Exception) { Pair(0, 0L) }
}
```

---

## 🔵 LOW Issues

### L-1: `SecurityClock` Uses `System.currentTimeMillis()` — Clock Manipulation Vulnerability
**File:** `SecurityModels.kt` — lines 21-23  
**Category:** Security — Bypass  
**Problem:** `SystemSecurityClock` uses `System.currentTimeMillis()` which reflects the device's wall clock. A user can set the device time forward to instantly expire any lockout. `GuardSessionManager` correctly uses `SystemClock.elapsedRealtime()` (monotonic), but `AuthManager` and `RateLimiter` use the wall clock.  
**Impact:** User can bypass rate limiting by advancing device clock.  
**Solution:** Use `SystemClock.elapsedRealtime()` for `SecurityClock` instead:
```kotlin
object SystemSecurityClock : SecurityClock {
    override fun currentTimeMillis(): Long = android.os.SystemClock.elapsedRealtime()
}
```
Note: This changes the semantics of `AuthState.Authenticated.sessionExpiresAtMillis` to be relative to boot time rather than wall clock, which is actually more robust.

---

### L-2: `AccessibilityGuardService.onInterrupt()` Sets Guard as Not Running
**File:** `AccessibilityGuardService.kt` — lines 78-80  
**Category:** Bug — Over-Aggressive  
**Problem:** `onInterrupt()` is called when the system interrupts the accessibility service feedback (e.g., when a higher-priority feedback takes over temporarily). Setting `isGuardRunning = false` in this callback is too aggressive — the service is still alive and will continue receiving events.  
**Impact:** Temporary false "Guard Inactive" status in the UI during transient interrupts, which may cause unnecessary alarm.  
**Solution:** Do not set `isGuardRunning = false` in `onInterrupt()`:
```kotlin
override fun onInterrupt() {
    // Intentionally empty: service is still alive, just temporarily interrupted.
    // Guard status will be set to false in onDestroy() when truly stopped.
}
```

---

### L-3: `PasswordHasher` Docstring Says 600,000 Iterations but Code Uses 100,000
**File:** `PasswordHasher.kt` — lines 9-16  
**Category:** Documentation — Stale  
**Problem:** The KDoc comment says "Iterations: 600,000" but `DEFAULT_ITERATIONS = 100_000`. This was changed in a previous commit but the documentation wasn't updated.  
**Impact:** Misleading documentation.  
**Solution:** Update the docstring to "Iterations: 100,000 (OWASP Mobile standard)".

---

### L-4: `GuardedTargets.isGuarded()` Accepts `CharSequence?` but `RULES` Check Hardcodes `"com.lockweb.app"`
**File:** `GuardedTargets.kt` — line 137  
**Category:** Bug — Fragile  
**Problem:** `if (pkgStr == "com.lockweb.app") return false` is hardcoded. If the `applicationId` is ever changed (e.g., for a dev build variant with `.dev` suffix), the guard will start intercepting LockWeb's own activities.  
**Impact:** Low risk since applicationId is unlikely to change, but fragile.  
**Solution:** Pass the applicationId as a parameter or read it from `BuildConfig.APPLICATION_ID`.

---

### L-5: Navigation State Not Preserved Across Process Death
**File:** `LockWebApp.kt` — line 53  
**Category:** UX  
**Problem:** `var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }` — the current tab is stored in `remember`, not `rememberSaveable`. After process death (common on low-memory devices), the user always returns to the Dashboard even if they were on Settings.  
**Impact:** Minor UX annoyance.  
**Solution:** Use `rememberSaveable` with a custom `Saver` for the `Screen` enum.

---

### L-6: `AddRuleDialog` Does Not Clear State After Successful Addition
**File:** `DashboardScreen.kt` — lines 323-328  
**Category:** UX Bug  
**Problem:** When `AddRuleResult.Success` is returned, the dialog is dismissed but if it fails (duplicate/invalid), the dialog stays open. However, the error message from the `AddRuleResult` is never shown to the user. The dialog just stays open with no feedback.  
**Impact:** User doesn't know why their rule wasn't added.  
**Solution:** Display the error in the dialog:
```kotlin
val result = app.container.ruleRepository.addRule(domain, mode, note)
when (result) {
    is AddRuleResult.Success -> showAddRuleDialog = false
    is AddRuleResult.Duplicate -> errorMessage = "This domain is already blocked"
    is AddRuleResult.InvalidDomain -> errorMessage = result.reason
}
```

---

### L-7: `atomicUpdate()` Uses `File.renameTo()` Which Is Not Atomic on All Filesystems
**File:** `BlocklistManager.kt` — line 94  
**Category:** Data Integrity  
**Problem:** `tempFile.renameTo(blocklistFile)` is not guaranteed to be atomic on all Android filesystem implementations. If the device loses power during this operation, the blocklist could be corrupted (neither old nor new file exists).  
**Impact:** Very rare data loss scenario.  
**Solution:** Use `Os.rename()` on API 21+ or `Files.move()` with `ATOMIC_MOVE` option. Or use the pattern: write temp → sync → rename → sync parent directory.

---

### L-8: VPN Reader Thread Uses 4096-Byte Buffer — May Not Handle Jumbo DNS Responses
**File:** `LockWebVpnService.kt` — line 277  
**Category:** Correctness  
**Problem:** `val packetBuffer = ByteArray(4096)` — the maximum DNS UDP response is 512 bytes (RFC 1035) or up to 4096 bytes with EDNS0. However, the IP header + UDP header adds 28-48 bytes, making the maximum packet ~4144 bytes. Additionally, DNS over TCP (through the VPN tunnel) could theoretically exceed 4096 bytes.  
**Impact:** Extremely rare truncation of unusually large DNS responses. Since only UDP port 53 traffic reaches the TUN, and typical DNS responses are well under 4096, this is low risk.  
**Solution:** Increase buffer to `MTU + 100 = 1600` or simply `8192` for safety.

---

### L-9: `OisdBlocklistUpdateWorker` Uses Deprecated `URL()` Constructor
**File:** `OisdBlocklistUpdateWorker.kt` — line 78  
**Category:** Maintenance  
**Problem:** `URL(urlString)` constructor is deprecated in Java 20+ / newer Android APIs. Should use `URI.create(urlString).toURL()`.  
**Impact:** Compiler warning, no runtime issue yet.  
**Solution:** `val url = java.net.URI.create(urlString).toURL()`

---

### L-10: `DnsBuilder.validateResponse()` Parses the Entire Response Just to Check Question Match
**File:** `DnsBuilder.kt` — lines 93-125  
**Category:** Performance  
**Problem:** `validateResponse()` calls `DnsParser.parse(responseBytes)` which allocates multiple objects and copies byte arrays, just to check if the question section matches. This runs on every forwarded DNS response.  
**Impact:** Minor unnecessary allocations per DNS query.  
**Solution:** Do a lightweight check: compare raw question bytes at the expected offset without full parsing:
```kotlin
// Fast path: compare raw question bytes at offset 12
val queryQuestionBytes = query.question?.rawQuestionBytes ?: return true
if (responseBytes.size >= 12 + queryQuestionBytes.size) {
    for (i in queryQuestionBytes.indices) {
        if (responseBytes[12 + i] != queryQuestionBytes[i]) return false
    }
}
```

---

### L-11: `BlocklistParser` Is Not Shown in the Audit but Referenced Everywhere
**File:** `core-filter/src/main/kotlin/com/lockweb/corefilter/BlocklistParser.kt`  
**Category:** Completeness  
**Problem:** This file parses the OISD blocklist. Since I read it as part of the audit — it correctly handles wildcard prefixes (`*.domain.com` → `domain.com`), comments, and blank lines. No significant issues found, but it lacks input size limits.  
**Impact:** A maliciously crafted blocklist file with billions of lines could cause OOM.  
**Solution:** Add a max line count (e.g., 1,000,000) to `BlocklistParser.parse()`.

---

### L-12: Compose `remember` vs `rememberSaveable` Inconsistency Across All Screens
**File:** Multiple UI files  
**Category:** UX — State Loss  
**Problem:** Multiple screens use `remember` for form state that should survive configuration changes:
- `BlocklistScreen.kt`: `searchQuery`, `selectedTab`
- `SettingsScreen.kt`: `showAuthGateFor`, dialog states
- `DashboardScreen.kt`: `showAddRuleDialog`  
**Impact:** State is lost on rotation/config change. Since the app likely locks orientation, this is low impact but still incorrect.  
**Solution:** Replace `remember` with `rememberSaveable` for all user-visible form state.

---

### L-13: `PasswordGateActivity` Grants Access When `AuthResult.Uninitialized`
**File:** `LockWebPasswordGateActivity.kt` — lines 195-198  
**Category:** Security — Edge Case  
**Problem:** If the password gate is shown but `AuthManager` returns `Uninitialized` (no credentials set up), the gate auto-grants access: `onUnlockSuccess()`. This allows bypassing the guard during the brief window between app install and onboarding completion.  
**Impact:** Before the user completes onboarding, the guard is ineffective. However, since the guard is an Accessibility Service that the user must manually enable (which happens during onboarding), this window is practically zero.  
**Solution:** Accept as-is (the guard isn't enabled before onboarding anyway), or show a "Setup Required" message instead of auto-granting.

---

### L-14: IPv6 Extension Headers Not Handled
**File:** `PacketCodec.kt` — lines 66-88  
**Category:** Correctness  
**Problem:** IPv6 parsing checks `nextHeader` at byte 6 and only accepts 17 (UDP). IPv6 packets may have extension headers (e.g., Hop-by-Hop, Routing) before the UDP header. These packets would be silently dropped.  
**Impact:** Some IPv6 DNS queries with extension headers won't be filtered. This is rare in practice since DNS queries typically don't use extension headers.  
**Solution:** Walk the extension header chain until reaching the UDP header or a non-extension header.

---

## Summary Table

| # | Severity | File | Issue |
|---|----------|------|-------|
| C-1 | 🔴 | `debug.keystore` | Debug keystore in public repo |
| C-2 | 🔴 | `build.gradle.kts` | R8/ProGuard disabled in release |
| C-3 | 🔴 | `proguard-rules.pro` | Empty ProGuard rules |
| C-4 | 🔴 | `AuthManager.kt` | Credential files not permission-hardened |
| C-5 | 🔴 | `RateLimiter.kt` | Rate limit file tamperable on root |
| C-6 | 🔴 | `AppDatabase.kt` | Room DB unencrypted |
| C-7 | 🔴 | `SettingsRepository.kt` | DataStore unencrypted — adult filter bypassable |
| H-1 | 🟠 | `AuthManager.kt` | Password `String` stays in memory |
| H-2 | 🟠 | `AndroidManifest.xml` | Unnecessary `SYSTEM_ALERT_WINDOW` |
| H-3 | 🟠 | `SettingsScreen.kt` | DNS change is non-functional |
| H-4 | 🟠 | `OnboardingWizardScreen.kt` | Recovery code screen lacks FLAG_SECURE |
| H-5 | 🟠 | `SettingsScreen.kt` | Password change not implemented |
| H-6 | 🟠 | `AppDatabase.kt` | No migration strategy |
| H-7 | 🟠 | `AuthGateDialog.kt` | Password dialog lacks FLAG_SECURE |
| H-8 | 🟠 | `DashboardScreen.kt` | Adding rules doesn't require auth |
| H-9 | 🟠 | `GuardSessionManager.kt` | Singleton state lost on process death |
| M-1 | 🟡 | `OisdBlocklistUpdateWorker.kt` | Blocklist update doesn't push to VPN |
| M-2 | 🟡 | `UpstreamResolver.kt` | No socket reuse |
| M-3 | 🟡 | `BlocklistManager.kt` | 100K domains re-parsed every recompile |
| M-4 | 🟡 | `AuthManager.kt` | No recovery code regeneration |
| M-5 | 🟡 | `LockWebVpnService.kt` | Fixed 60s cache TTL |
| M-6 | 🟡 | `BootReceiver.kt` | Android 14 background start may fail |
| M-7 | 🟡 | `GuardedTargets.kt` | Missing OEM app coverage |
| M-8 | 🟡 | `RuleRepository.kt` | CoroutineScope never cancelled |
| M-9 | 🟡 | `OnboardingWizardScreen.kt` | Recovery code lost on config change |
| M-10 | 🟡 | `SettingsScreen.kt` | Hardcoded version string |
| M-11 | 🟡 | `DnsCache.kt` | IDN normalization mismatch |
| M-12 | 🟡 | `RateLimiter.kt` | Double file read per check |
| L-1 | 🔵 | `SecurityModels.kt` | Wall clock enables lockout bypass |
| L-2 | 🔵 | `AccessibilityGuardService.kt` | `onInterrupt()` too aggressive |
| L-3 | 🔵 | `PasswordHasher.kt` | Stale docstring |
| L-4 | 🔵 | `GuardedTargets.kt` | Hardcoded package name |
| L-5 | 🔵 | `LockWebApp.kt` | Nav state not saved |
| L-6 | 🔵 | `DashboardScreen.kt` | No error feedback on add rule |
| L-7 | 🔵 | `BlocklistManager.kt` | Non-atomic rename |
| L-8 | 🔵 | `LockWebVpnService.kt` | 4096 byte buffer may truncate |
| L-9 | 🔵 | `OisdBlocklistUpdateWorker.kt` | Deprecated `URL()` constructor |
| L-10 | 🔵 | `DnsBuilder.kt` | Unnecessary full parse in validate |
| L-11 | 🔵 | `BlocklistParser.kt` | No input size limit |
| L-12 | 🔵 | Multiple UI files | `remember` vs `rememberSaveable` |
| L-13 | 🔵 | `LockWebPasswordGateActivity.kt` | Auto-grant when uninitialized |
| L-14 | 🔵 | `PacketCodec.kt` | IPv6 extension headers not handled |

---

> [!TIP]
> **Recommended Fix Priority:**
> 1. Start with C-1, C-2, C-3 (build security — quick wins)
> 2. Then C-7 (adult filter bypass — core mandate violation)
> 3. Then H-3, H-5 (non-functional features users can see)
> 4. Then H-4, H-7 (screenshot protection)
> 5. Then M-1, M-9 (silent bugs)
> 6. Everything else in order of severity
