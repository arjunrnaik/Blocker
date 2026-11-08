# LockWeb Known Limitations

In accordance with `MASTER_PROMPT.md` Section 15, this document openly and transparently lists all known technical boundaries and limitations of LockWeb V1.

---

## 1. Network & Protocol Limitations

* **DNS-Only Architecture:** LockWeb intercepts only port 53 UDP DNS traffic. It does not inspect TCP payloads, HTTP/HTTPS connection streams, or general internet traffic.
* **Direct-IP Connections:** If an application connects directly to a hardcoded numeric IP address (e.g. `198.51.100.1`) without performing a standard DNS query, LockWeb cannot intercept it.
* **Hardcoded DNS & Proxies:** Applications that implement their own internal DNS-over-HTTPS (DoH) client or route through their own proxy protocols bypass local port 53 DNS filtering.
* **Encrypted Client Hello (ECH) & QUIC:** LockWeb does not inspect TLS handshakes, Server Name Indication (SNI), or QUIC transport packets.
* **Single VPN Slot Constraint:** Android permits only one active `VpnService` at a time. If another VPN or Tor service is started, Android automatically revokes LockWeb's VPN interface. LockWeb immediately detects this, marks health as `Degraded`, and posts a high-priority warning notification.
* **Alternative Domains & CDNs:** Blocking a main domain (e.g., `reddit.com`) does not automatically block completely distinct alternative web domains, API endpoints, or CDNs unless they are also added as rules.
* **Generic Browser Error Page:** When a site is blocked, LockWeb returns a synthetic `NXDOMAIN` response. Modern browsers render their built-in error screen (e.g., *"This site can't be reached"* or `DNS_PROBE_FINISHED_NXDOMAIN`), rather than a custom branded block screen. (Serving a custom HTML block page requires a local HTTP web server and TLS interception, which is planned for V2).

---

## 2. DNS Bypass Vectors (DoH / DoT)

* **Android Private DNS (DNS-over-TLS):** When Android's Private DNS feature is enabled in "Strict" or "Automatic" mode, Android routes all DNS queries over TLS directly to an external resolver, completely bypassing the local VPN interface.
  * *Mitigation:* LockWeb actively monitors network link properties, detects active Private DNS, displays a warning status card, and guards the Android Private DNS settings dialog with the master password.
* **Chrome Secure DNS (DNS-over-HTTPS):** If Google Chrome has "Use secure DNS" turned on, Chrome may query upstream DoH resolvers directly.
  * *Mitigation:* LockWeb's Anti-Tamper Guard intercepts navigation to Chrome's Secure DNS settings dialog with the master password, and blocks known public DoH bootstrap hostnames locally.

---

## 3. Adult Content Filter Limitations

* **Community Blocklist Coverage:** The adult content blocklist is powered by the open-source OISD NSFW list (~100,000+ domains). While comprehensive, no blocklist contains 100% of the internet. Newly registered or obscure adult websites may not be blocked until added in upstream OISD releases.
* **Update Frequency:** LockWeb syncs OISD updates weekly via `WorkManager`. Domains created between sync cycles may not be blocked immediately unless manually added to user rules.

---

## 4. Operating System & Self-Protection Boundaries

* **Safe Mode Reboot:** Powering off the device and booting into Android Safe Mode disables all non-system services, including LockWeb's `VpnService`, `AccessibilityGuardService`, and `DeviceAdminReceiver`.
  * *Status:* This is an architectural constraint of standard non-root Android user space. LockWeb's `BootReceiver` restarts protection immediately upon normal reboot. Total elimination of Safe Mode is an enterprise feature planned for V2 (Device Owner mode).
* **ADB Debugging:** A user with physical access to a computer with Android Debug Bridge (ADB) installed can issue `adb uninstall com.lockweb.app` or disable services over USB.
  * *Mitigation:* Users are advised to disable Developer Options and USB Debugging on their phone.
* **Hardware Factory Reset:** Booting into hardware recovery mode and performing a full factory reset erases all user data and third-party applications. This cannot be prevented by any third-party Android application.
* **Always-On VPN Configuration:** Android requires the user to manually toggle "Always-on VPN" and "Block connections without VPN" in system settings. Third-party apps cannot force this setting on without Device Owner privileges.
