# LockWeb — VPN Architecture & DNS-Only Filtering Design

## 1. Overview & Core Decision

LockWeb employs a **DNS-only local VPN architecture**. 
Unlike traditional VPNs that route all device IP traffic through a remote tunnel, LockWeb routes **only DNS queries** into a local virtual network interface (`TUN`), allowing all general TCP, UDP, and web payloads to bypass the VPN naturally without overhead, battery drain, or complex user-space TCP/IP stacks.

---

## 2. Android VpnService Configuration

### 2.1 Virtual Network Topology
- **Local TUN Interface Addresses:**
  - IPv4: `10.111.222.2/32`
  - IPv6: `fd00:1::2/128` (Unique Local Address)
- **Virtual DNS Resolver Addresses:**
  - IPv4: `10.111.222.1`
  - IPv6: `fd00:1::1`
- **Strict Split-Tunnel Routing (No Default Route):**
  - Routes added via `VpnService.Builder.addRoute`:
    - `10.111.222.1/32`
    - `fd00:1::1/128`
  - **CRITICAL:** No default route (`0.0.0.0/0` or `::/0`) is added. Because the operating system only routes packets destined for the fake DNS servers into the TUN interface, all non-DNS traffic leaves the device directly over Wi-Fi or Cellular interfaces.
- **MTU:** Set explicitly to `1500` bytes (standard Ethernet frame MTU). DNS messages over UDP fit comfortably without fragmentation.

### 2.2 Android API Verification & Facts Relied Upon
1. **User Consent via `VpnService.prepare()`:**
   - On Android, apps cannot start a VPN without explicit user consent.
   - `VpnService.prepare(context)` returns `null` if consent has already been granted, or an `Intent` that must be launched via `ActivityResultLauncher` to display the system dialog.
2. **Socket Protection via `VpnService.protect()`:**
   - Any UDP or TCP socket used by the VPN service to communicate with the upstream resolver (e.g., Cloudflare `1.1.1.1:53`) MUST call `protect(socket)`.
   - This binds the socket to the underlying physical network and exempts it from VPN routing, preventing infinite routing loops.
3. **Android 14+ Foreground Service Requirements:**
   - Under Android 14 (API 34) and Android 15, background execution of VPNs requires declaring `android:foregroundServiceType="specialUse"` paired with `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="vpn" />` and `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`.

---

## 3. Packet Processing & Forwarding Pipeline

```
[ TUN Interface (File Descriptor) ]
               │
               ▼ (Read Raw IP Packet)
   [ PacketCodec.parse() ] ─── Malformed / Non-UDP ───► [ DROP ]
               │
               ▼ (Valid UDP to Port 53)
     [ DnsParser.parse() ] ─── Malformed DNS ─────────► [ DROP ]
               │
               ▼ (Valid DNS Query)
      [ RuleSet.matches() ]
          ├── MATCHED (Blocked) ──► Increment Counter ──► [ DnsBuilder.buildNxdomain() ]
          │                                                       │
          │                                                       ▼
          │                                           [ PacketCodec.buildReply() ]
          │                                                       │
          │                                                       ▼
          │                                         [ Write reply to TUN fd ]
          │
          └── ALLOWED ──► [ Check DnsCache ]
                                ├── HIT ──► [ Write cached reply to TUN fd ]
                                └── MISS ─► [ Forward via protect() Socket ]
                                                  │
                                                  ▼ (Upstream 1.1.1.1:53)
                                            [ Receive response ]
                                                  │
                                                  ▼ (If TC=1, retry via TCP)
                                            [ Cache & write to TUN fd ]
```

### 3.1 Upstream Forwarding & Retry Logic
- Allowed queries are forwarded to upstream resolver (default: Cloudflare `1.1.1.1`, fallback `1.0.0.1`).
- Socket timeout: 2,500ms.
- On timeout: retry once. If the second attempt fails, return **SERVFAIL** (RFC 1035 RCODE=2). Failures are never turned into NXDOMAIN, ensuring network errors are never confused with blocked sites.
- Truncation Handling: If the upstream UDP response has the `TC` (Truncation) bit set, the service retries upstream over a `protect()`-ed TCP socket and returns the complete response.

---

## 4. Health & State Model

The service exposes an honest `StateFlow<VpnStatus>`:
- `Idle`: Service stopped.
- `Starting`: Service initializing.
- `Active`: TUN interface established, read loop running, and self-test passed.
- `Paused`: User temporarily paused filtering.
- `PermissionRequired`: `VpnService.prepare()` requires user consent.
- `Degraded(reason)`: TUN alive but upstream DNS failing.
- `Error(reason)`: Unrecoverable failure.

The UI displays "Active" ONLY when verified by live state; it is never hardcoded.

---

## 5. Privacy & Zero-Logging Guarantee
- Zero logging of requested domain names in Logcat, Room, files, or notifications.
- Block counter is an anonymous, increment-only `AtomicLong`.
