# 🛡️ LockWeb — Self-Control Website & Adult Content Blocker for Android

> **LockWeb is a hardened self-control tool designed for one purpose: you cannot disable or bypass your website blocks or adult content filter without entering the master password.**

---

## Key Features

* **DNS-Only Local VPN Loopback:** Intercepts port 53 UDP DNS queries locally. Routes zero general internet traffic (`0.0.0.0/0`) through the VPN, resulting in **< 1% battery consumption** per day and full native connection speeds.
* **Instant Synthetic NXDOMAIN:** Blocked websites immediately return `NXDOMAIN` without reaching the network.
* **Irreversible Permanent Adult Content Filter:** Bundles the OISD NSFW blocklist (100,000+ domains). Once opted in, **it cannot be disabled through any code path or setting, even with the master password.**
* **Anti-Tamper Guard (`AccessibilityGuardService`):** Intercepts attempts to open Android Settings (Force Stop, Clear Data, VPN disconnection, Device Admin deactivation, Private DNS toggles), Chrome Secure DNS toggles, and Play Store uninstallation with a full-screen, uncancelable Password Gate.
* **Device Administrator (Anti-Uninstall Shield):** Prevents direct launcher or app-drawer uninstallation while active.
* **Boot Auto-Start Receiver:** Automatically resumes DNS filtering and protection immediately upon device reboot.
* **Honest Protection Health Model:** Security status is never hardcoded; the app only displays "Fully Protected" when the VPN TUN is confirmed active, the Anti-Tamper Guard is connected, and Device Administrator is active.
* **Zero Telemetry & Zero Logging:** No analytics, no ads, no trackers, and no domain names are ever written to disk, database, or Android system logs (`Logcat`). Screen scraping is mathematically impossible (`canRetrieveWindowContent = false`).

---

## Architecture Overview

```
                      ┌──────────────────────────────────────┐
                      │              Android OS              │
                      └──────────────────┬───────────────────┘
                                         │
                   ┌─────────────────────┴─────────────────────┐
                   │                                           │
         [ Network Queries ]                           [ GUI Navigation ]
                   │                                           │
                   ▼                                           ▼
      ┌─────────────────────────┐                 ┌─────────────────────────┐
      │   LockWebVpnService     │                 │ AccessibilityGuardSvc   │
      │   (Local TUN Loopback)  │                 │ (Anti-Tamper Monitor)   │
      └────────────┬────────────┘                 └────────────┬────────────┘
                   │                                           │
         ┌─────────┴─────────┐                       ┌─────────┴─────────┐
         │                   │                       │                   │
      [Allowed]          [Blocked]                [Allowed]          [Guarded]
         │                   │                       │                   │
         ▼                   ▼                       ▼                   ▼
    Forward to          Synthetic                Standard            Full-Screen
   Upstream DNS          NXDOMAIN               Interaction         Password Gate
   (1.1.1.1:53)        (Blocked IP)                                  (FLAG_SECURE)
```

---

## How to Build & Download the APK

LockWeb uses GitHub Actions CI to produce automated, cryptographically signed debug APKs.

1. Go to the [GitHub Actions tab](https://github.com/arjunrnaik/Blocker/actions).
2. Select the latest completed run on `main`.
3. Under **Artifacts**, click **`lockweb-debug-apk`** to download the zip containing `app-debug.apk`.
4. Transfer or download the APK directly onto your Android phone.

---

## Step-by-Step Installation & Setup

### 1. Initial Sideloading
1. Open the downloaded APK on your Android 14+ device and tap **Install**.
2. If Google Play Protect warns about sideloading, tap **More details → Install anyway**.

### 2. Bypass Play Protect "Restricted Settings" (Android 14+)
Android 14+ automatically restricts Accessibility Services for sideloaded apps. To permit LockWeb:
1. Long-press the **LockWeb** app icon → tap **App info** (ℹ️).
2. Tap the **three dots (⋮)** in the top right corner.
3. Tap **Allow restricted settings** and confirm with your phone PIN/fingerprint.
4. *(If Google Play Protect displays "App blocked to protect your device", open the Google Play Store app → Profile icon → Play Protect → Settings ⚙️ → Toggle off "Scan apps with Play Protect" during installation).*

### 3. Complete the Onboarding Wizard
Launch LockWeb and complete the 6-step setup:
1. **Welcome:** Review the self-control philosophy and security model.
2. **Set Master Password:** Create a strong password (minimum 8 characters).
3. **Emergency Recovery Code:** Securely copy your 40-character hex recovery code.
4. **Adult Content Filter:** Choose whether to lock the 100k+ NSFW blocklist permanently.
5. **Activate Protection Layers:**
   - **DNS Filtering (VPN):** Tap **Grant** → Confirm Android's VPN connection prompt.
   - **Anti-Tamper Guard:** Tap **Enable** → Toggle ON **LockWeb** in Accessibility Settings.
   - **Device Administrator:** Tap **Activate** → Confirm **Activate this device admin app**.
   - **Battery Exemption:** Tap **Exempt** → Tap **Allow** (prevents background sleeping).
6. **Final Verification:** Tap **Complete Setup** to enter your Dashboard.

---

## How to Configure "Always-On VPN & Lockdown"

For a 100% leak-proof system guarantee:
1. Open LockWeb → tap the **Protection** tab.
2. On card **4. Always-On VPN & Lockdown**, tap **Configure**.
3. In Android's VPN settings list, tap the **Gear ⚙️ icon** next to **LockWeb**.
4. Turn ON:
   - **Always-on VPN**
   - **Block connections without VPN** *(VPN Lockdown)*
5. Return to LockWeb — card 4 will show **Active & Locked ✅**.

---

## How to Uninstall (When Legitimate)

To prevent self-sabotage, uninstallation requires the master password:
1. Open **LockWeb** → Go to the **Settings** tab.
2. Scroll down to **⚠️ Danger Zone: Disable & Uninstall**.
3. Tap **Disable All Protection**.
4. Enter your **Master Password**.
5. Confirm the dialog: LockWeb removes the Device Administrator, pauses the VPN filter, and opens a 60-second administrative window in the Anti-Tamper Guard.
6. You may now cleanly uninstall the app from Android Settings or your launcher.

---

## Technical Documentation

* [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — Multi-module architecture, Room SQLite, KeyStore PBKDF2 vault.
* [docs/VPN_DESIGN.md](docs/VPN_DESIGN.md) — DNS-only VPN loopback, non-blocking UDP forwarding, zero data traffic interception.
* [docs/ANTI_TAMPER_GUIDE.md](docs/ANTI_TAMPER_GUIDE.md) — How the Accessibility Guard intercepts Settings and Play Store uninstallation.
* [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) — Complete analysis of every bypass attack vector and LockWeb's mitigations.
* [docs/PRIVACY.md](docs/PRIVACY.md) — Plain-language privacy contract guaranteeing zero telemetry and zero screen scraping.
* [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) — Transparent documentation of system boundaries and known limitations.
* [docs/MANUAL_TEST_PLAN.md](docs/MANUAL_TEST_PLAN.md) — Step-by-step physical device test matrix and battery measurement protocol.

---

## License

Personal self-control software. Built with Kotlin, Jetpack Compose, and Android Jetpack.
