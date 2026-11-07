# LockWeb Privacy & Security Guarantees

LockWeb is built with a strict "privacy-by-design" and "zero-telemetry" architecture. The application is designed for absolute self-control while maintaining the highest standard of personal privacy.

---

## 1. Anti-Tamper Guard (`AccessibilityGuardService`)

LockWeb requires Android's **Accessibility Service** permission (`BIND_ACCESSIBILITY_SERVICE`) exclusively to prevent impulsive disabling, tampering, or uninstallation.

### What the Guard Inspects:
- **Foreground Component Identification:** The service only observes `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` events to inspect `event.packageName` and `event.className`.
- **Target Matching:** It compares these identifiers against a strict list of guarded system destinations (such as Android Settings App Info, Device Admin settings, VPN configuration, Private DNS dialogs, and Google Chrome Secure DNS toggles).

### What the Guard DOES NOT Do:
- **No Window/Screen Content Scraping:** In `accessibility_service_config.xml`, `canRetrieveWindowContent` is explicitly set to `false`. The service is cryptographically and architecturally barred by Android from reading screen text, UI elements, button labels, messages, emails, photos, or passwords.
- **No Keystroke / Input Logging:** LockWeb cannot and does not inspect keyboard input, typed text, or form fields.
- **No User Activity Tracking:** The service does not log or record apps opened or time spent in other applications.

---

## 2. DNS Filtering Engine (`LockWebVpnService`)

LockWeb utilizes a local, DNS-only VPN loopback (`VpnService`).

### Zero Domain Logging:
- **No Query Storage:** Domain names processed by the DNS codec are analyzed in-memory against local Radix/Trie rule sets and immediately discarded.
- **No Disk Logging:** Domain names are **never** written to log files, SQLite/Room databases, DataStore preferences, crash dumps, or Android system logs (`Logcat`).
- **Anonymous Counters Only:** The application only maintains an anonymous, increment-only integer counter of blocked queries for diagnostic status. This counter contains zero references to which domains were queried.
- **No Remote Proxying:** Only synthetic DNS IP traffic (`10.111.222.1:53` and `fd00:1::1:53`) is routed to the TUN interface. General internet traffic (`0.0.0.0/0`) is **never** routed through the VPN, eliminating battery drain and ensuring your web traffic never passes through a third-party server.

---

## 3. Cryptographic Security & Passwords

- **PBKDF2 Password Hashing:** Passwords are never stored in plaintext. Passwords are salted with 16 cryptographically random bytes and hashed using `PBKDF2WithHmacSHA256` with 600,000 iterations.
- **Hardware Keystore Protection:** Credential records are encrypted using AES-256-GCM via the hardware-backed `AndroidKeyStore`.
- **Screen Protection (`FLAG_SECURE`):** The `LockWebPasswordGateActivity` applies `WindowManager.LayoutParams.FLAG_SECURE` to prevent screenshots, screen recording, and exposure in recent task thumbnails.

---

## 4. Telemetry & Network Usage

- **Zero Analytics:** LockWeb contains no analytics SDKs (no Firebase, no Google Analytics, no telemetry trackers).
- **Network Access:** Network access is utilized exclusively for:
  1. Forwarding allowed DNS queries to the user's chosen upstream DNS resolver over standard UDP/53.
  2. Periodic weekly download of the public OISD NSFW blocklist from `https://nsfw.oisd.nl/domainswild`.
