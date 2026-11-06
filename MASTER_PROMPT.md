# MASTER PROMPT — LockWeb (Android Self-Control Website Blocker)

Paste this whole file into Antigravity as the project brief. For step-by-step building, use PHASED_PROMPTS.md alongside it.

---

## 1. Role and ground rules

Act as a senior Android engineer, network engineer, and QA engineer. Build an Android app named **LockWeb**: a password-protected, tamper-resistant website blocker for personal self-control use on one Android phone.

**The user IS the adversary.** LockWeb's job is to stop impulsive access to distracting and adult websites. Every design decision must assume the user will try to bypass the blocker in a moment of weakness.

Work directly in the project workspace. Create real project files, implement features, write tests, and document the result.

Honesty rules (these override everything else):
- Never claim a feature works unless code and tests support it.
- Never show "Protected" in the UI unless the app has verified that filtering is actually running AND the Accessibility Guard is active.
- If something cannot be done with official Android APIs, say so in the UI and docs. Do not fake it.
- No hidden master password, no hardcoded PIN, no undocumented bypass, no hidden APIs, no exploits.

## 2. Build environment (important)

- I am NOT using Android Studio. The editor is Antigravity.
- Builds happen in **GitHub Actions** (see `.github/workflows/build-apk.yml`). The workspace may not have the Android SDK installed.
- If the Android SDK is not available locally, do not pretend to have compiled the app. Write code carefully, keep changes small, and rely on CI. When CI fails, I will paste the log and you will fix the cause.
- Create the Gradle wrapper (`gradlew`, `gradle/wrapper/*`) and commit it. CI depends on it.
- Use Gradle Kotlin DSL and a version catalog (`gradle/libs.versions.toml`).
- JDK 17 for builds.
- Do not guess dependency versions. Look up current stable versions of the Android Gradle Plugin, Kotlin, Compose BOM, Room, DataStore, and coroutines, and pick a mutually compatible set. Write the chosen versions and the reason into `docs/ARCHITECTURE.md`.
- `minSdk = 34`. `compileSdk` and `targetSdk` = the latest stable API level supported by the chosen AGP.
- Package name: `com.lockweb.app`.
- **Debug signing:** commit a fixed debug keystore (debug keystores are not secret) and configure `signingConfigs.debug` to use it. Reason: GitHub runners generate a new debug key every build, and Android refuses to update an installed app signed with a different key. With Device Admin active, the app cannot be uninstalled to work around this, so a stable signature is required.
- **Distribution:** sideloaded APK from GitHub Actions artifacts only. No Google Play Store. This means we are free to use Accessibility Service and Device Admin without Play Store policy restrictions.

## 3. Fixed product decisions

| Topic | Decision |
|---|---|
| Purpose | **Self-control blocker.** The user is the adversary. The app must resist impulsive bypass attempts. |
| Filtering engine | **DNS-only local VPN.** Only DNS traffic enters the TUN interface. All other traffic bypasses the VPN naturally. No user-space TCP/IP stack. |
| Anti-tamper guard | **AccessibilityService** that blocks access to Android Settings, Force Stop, Clear Data, Device Admin deactivation, VPN settings, and Play Store uninstall for LockWeb — unless the master password is entered first. |
| Uninstall guard | **DeviceAdminReceiver** that prevents casual uninstall via launcher or drag-to-uninstall. |
| Blocked reply | **NXDOMAIN** for every query type (A, AAAA, HTTPS/SVCB, anything) on a blocked name. |
| Block notification | When a blocked domain is queried, increment an anonymous counter on the dashboard ("X blocked today"). The browser will show a generic "This site can't be reached" error. **No domain names in any notification or log.** |
| Adult content blocklist | **OISD NSFW blocklist**, bundled on first install and auto-updated weekly over HTTPS. **Once enabled during setup, the adult blocklist is PERMANENT — it cannot be removed or disabled even with the master password.** Only a factory reset (or clearing app data, which the Accessibility Guard blocks) removes it. |
| User-added rules | Manually added domains/URLs. Can be added without authentication. Removal and editing require the master password. |
| Blocking schedule | **Always-on, 24/7.** No time-based schedules. If a domain is on the list, it is always blocked. |
| Upstream DNS | Default **Cloudflare 1.1.1.1 / 1.0.0.1** and 2606:4700:4700::1111. User can change it in Settings after authenticating. |
| DoH / Private DNS mitigation | During onboarding, guide user to disable Android Private DNS and Chrome Secure DNS. The Accessibility Guard then **blocks access to those settings screens** to prevent re-enabling them impulsively. |
| Password hashing | **PBKDF2WithHmacSHA256**, 600,000+ iterations, 16-byte random salt, 256-bit output. Parameters stored with the hash so they can be raised later. |
| Password model | User knows their own password. Entering it grants immediate full access (no delay). |
| App lock | Locks **immediately on leaving the app** by default; timeout configurable. |
| Recovery | **One-time recovery code** shown once at setup, for the user to store safely (written down or in a password manager). Only a PBKDF2 hash of the code is stored. |
| Accountability partner | **None.** No external approval or shared codes. |
| App blocking | **No.** Websites/domains only. App blocking is out of scope. |
| Logging | **No logging of domains or browsing at all.** Only anonymous counters (total blocked count). No domain names in any log, notification, diagnostic, or export. |
| DI | Lightweight manual DI (an `AppContainer`). No Hilt, to reduce build risk. |
| Themes | Light and dark Material 3, follows system by default. |
| UI priority | **Minimal and functional.** Get blocking working reliably first. Polish later. |

## 4. Architecture

Three Gradle modules:

1. `:core-filter` — **pure Kotlin/JVM, no Android dependencies.** Contains:
   - Domain normalizer and matcher
   - Packet parsing (IPv4, IPv6, UDP headers) and packet building with correct checksums
   - DNS message parsing and building (including NXDOMAIN builder)
   - Rule compilation and blocklist merging
   All unit tests for this module run on a plain JVM in CI.
2. `:app` — Android app: Compose UI, ViewModels, Room, DataStore, Keystore, VpnService, AccessibilityService, DeviceAdminReceiver, boot receiver, blocklist updater.

Layers inside `:app`: `ui` → `domain` → `data`, plus `vpn`, `guard`, `security`, `admin`, `blocklist`. UI never touches the packet engine directly.

Keep code structured so a future multi-device feature could reuse `:core-filter` unchanged.

## 5. VPN design (DNS-only) — document before coding

Write the chosen design into `docs/VPN_DESIGN.md` before writing the engine. The expected design:

- `VpnService.Builder`:
  - Add a private TUN address (IPv4 and IPv6).
  - Add fake resolver addresses (for example `10.111.222.1` and an IPv6 ULA address) via `addDnsServer(...)`.
  - Add routes **only** for those resolver addresses (/32 and /128). Do NOT add a default route. This is what makes everything except DNS bypass the VPN, so no forwarding stack is needed.
  - Set MTU deliberately and document it.
- A single read loop reads packets from the TUN file descriptor. For each packet:
  - Parse IPv4/IPv6 and UDP. Accept only UDP to port 53 destined for a fake resolver address.
  - Parse the DNS question. If the name matches a rule (either user-added or from the adult blocklist), build an NXDOMAIN reply (same transaction ID, question echoed, QR/RD/RA flags set correctly) and write it back to the TUN as a correctly checksummed UDP packet with source/destination swapped.
  - If not matched, forward the original DNS payload to the upstream resolver over UDP using a socket protected with `VpnService.protect()`, then wrap the response and write it back to the TUN.
  - Validate upstream responses (transaction ID, question section). If the response is truncated (TC bit), retry upstream over TCP and return the result when it fits; otherwise return the truncated response.
  - On malformed or unsupported packets: drop safely. Never write malformed DNS back.
  - On upstream timeout: retry once, then return **SERVFAIL**. Never turn failures into NXDOMAIN (that would look like a block).
- Bounded concurrency (fixed-size dispatcher or bounded queue). No unbounded threads. Bounded buffers.
- Small DNS cache honoring TTLs, with a size limit.
- TCP DNS from clients to the fake resolver is a documented limitation (rare for normal lookups).
- Close the TUN file descriptor, sockets, and coroutines on stop and on `onRevoke()`.
- Rule updates: compile rules into an immutable structure and swap it atomically (for example via `StateFlow`/`AtomicReference`) so changes apply without restarting the VPN. Flush the DNS cache on rule changes.
- Foreground service type: **`specialUse`** with manifest property `PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "vpn"` and permission `FOREGROUND_SERVICE_SPECIAL_USE`. This is the correct type for VPN apps on Android 14+ (API 34).

## 6. Domain matching (standalone, in `:core-filter`)

Input accepted: `reddit.com`, `www.reddit.com`, `https://reddit.com/some/path`.

- Parse URLs with a real URI parser, not regex alone. Extract the host only; ignore paths, queries, ports, and userinfo.
- Lowercase, strip trailing dot, convert Unicode to ASCII using IDNA (`java.net.IDN` is acceptable; document its limits).
- Validate: label length 1–63, total length ≤ 253, allowed characters, no leading/trailing hyphens, no empty labels.
- Reject IP literals (IPv4 and IPv6) with a clear message: DNS blocking cannot see connections made directly to an IP address.
- Reject single-label names and bare public suffixes such as `com` or `co.uk`. Explain why. (Use a small embedded suffix list or a documented heuristic.)
- Two rule modes. **Default: hostname and subdomains.** Alternative: exact hostname only.
- Matching must use label boundaries. Never use substring matching.
- Required test cases:
  - Blocked `reddit.com` → blocks `reddit.com`, `www.reddit.com`, `old.reddit.com`, `a.b.reddit.com`.
  - Does NOT block `reddit.in`, `notreddit.com`, `reddit.com.example.org`, `xreddit.com`.
  - Exact-only rule blocks the exact host but not subdomains.
  - Trailing dots, mixed case, Unicode domains, Punycode, malformed URLs, IPv4/IPv6 literals, duplicates, overlapping rules (e.g. both `reddit.com` and `old.reddit.com`).
- Efficient lookup: check each suffix of the queried name against a hash map. Do not scan every rule per query.
- **Blocklist merging:** The compiled rule set is built from two sources merged together:
  1. User-added rules (from Room database).
  2. The OISD NSFW blocklist (from local cache file).
  The merge produces a single immutable rule set. User rules and blocklist rules are treated identically during matching. The origin (user vs. blocklist) is tracked only in the data layer for UI display purposes, not in the matching engine.

## 7. Anti-Tamper Guard (`AccessibilityGuardService`)

This is the critical self-control enforcement layer. Without it, the user can disable LockWeb in 5 seconds via Android Settings.

### 7.1. Purpose

The `AccessibilityGuardService` monitors which app/activity is in the foreground. When the user attempts to access any Android system screen that could disable, weaken, or uninstall LockWeb, the guard immediately launches a full-screen password gate activity. Without the correct master password, the user is sent back to the home screen.

### 7.2. Guarded targets

The guard intercepts navigation to the following:

| Target | Package / Activity Pattern | Why |
|---|---|---|
| LockWeb's App Info page | `com.android.settings` → App info for `com.lockweb.app` | Contains Force Stop, Clear Data, Uninstall, and Disable buttons |
| Device Admin settings | `com.android.settings` → Device Admin / Device Administrators | Contains the Deactivate button for LockWeb's DeviceAdminReceiver |
| VPN settings | `com.android.settings` → VPN preferences | Contains the toggle to disconnect LockWeb's VPN |
| Private DNS settings | `com.android.settings` → Private DNS | User could re-enable DoT to bypass DNS filtering |
| Accessibility settings | `com.android.settings` → Accessibility | User could disable the AccessibilityGuardService itself |
| Google Play Store (LockWeb page) | `com.android.vending` → LockWeb app details / uninstall dialog | User could uninstall via the Play Store app |
| Chrome Secure DNS settings | `com.android.chrome` → Settings → Privacy → Use secure DNS | User could re-enable DoH to bypass DNS filtering |

**Implementation notes:**
- Use `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` to detect the foreground activity's component name.
- Match against known activity class names and package names for the targets above.
- On OEM skins (Samsung One UI, Xiaomi MIUI, etc.), the Settings package name and activity names may differ. Include known variants and document how to add more.
- When a guarded target is detected:
  1. Immediately launch `LockWebPasswordGateActivity` as a new task with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.
  2. If the user enters the correct password → allow navigation (do not intercept for 60 seconds).
  3. If the user dismisses or fails → execute `GLOBAL_ACTION_HOME` to return to the home screen.
- The password gate activity uses `FLAG_SECURE` to prevent screenshots.

### 7.3. Self-protection

The guard must protect itself:
- If the user navigates to Accessibility settings (to disable the guard), the guard intercepts that too.
- If the guard service is killed by the OS, use `onServiceConnected()` / `onInterrupt()` lifecycle plus a watchdog (a periodic job that checks if the service is running and prompts the user to re-enable it if not).
- Post a persistent notification if the Accessibility Guard is not running: "⚠️ LockWeb protection is weakened — tap to re-enable."

### 7.4. Permissions required

- `BIND_ACCESSIBILITY_SERVICE` (declared in manifest, granted by user in Accessibility settings during onboarding).
- `SYSTEM_ALERT_WINDOW` ("Display over other apps") — for the password gate overlay. Request during onboarding.

### 7.5. What the guard does NOT do

- It does NOT read screen content, text fields, passwords, messages, or any personal data.
- It does NOT monitor which websites the user visits (that is the VPN layer's job, and the VPN does not log domains either).
- It ONLY reads the foreground window's package name and activity class name to determine if a guarded target is being accessed.
- Document this clearly in `docs/PRIVACY.md`.

## 8. Adult Content Blocklist

### 8.1. Source

- **Primary:** [OISD NSFW blocklist](https://nsfw.oisd.nl/domainswild) — curated, frequently updated, good coverage including South Asian domains.
- Format: one domain per line, plain text (hosts-file or domain-list format).
- License: free for personal use.

### 8.2. Lifecycle

1. **First install:** The APK bundles a snapshot of the OISD NSFW list as a raw asset. This ensures blocking works immediately without any network access.
2. **Weekly update:** A `WorkManager` periodic task (constrained to Wi-Fi + charging) downloads the latest list from the OISD URL over HTTPS. The downloaded list is validated (format check, size sanity check — reject if smaller than 50% of the current list to prevent a malicious empty update), then atomically replaces the cached copy.
3. **On update:** The blocklist manager merges the new list with user-added rules, compiles a new rule set, and hot-swaps it into the VPN engine. The DNS cache is flushed.

### 8.3. Permanence

- During onboarding, the user enables the adult content blocklist.
- Once enabled, a flag `adultBlocklistEnabled` is set to `true` in encrypted DataStore.
- **This flag can NEVER be set to `false` through any UI flow in the app, even after entering the master password.**
- The only way to remove the adult blocklist is to clear app data (which the Accessibility Guard blocks) or factory reset the phone.
- The password grants access to: adding/removing user rules, changing settings, disabling the VPN, deactivating Device Admin, uninstalling the app. But it does NOT grant access to removing the adult blocklist.
- This is communicated clearly during onboarding: "Once you enable the adult content filter, it cannot be turned off. This is permanent. The only way to remove it is a full factory reset of your phone."

### 8.4. Storage

- The blocklist is stored as a plain text file in internal storage (`filesDir`), not in Room. It can contain 100K+ domains and is parsed into the rule set on startup.
- Room stores only user-added rules (with metadata like notes, dates, categories).
- The compiled rule set merges both sources in memory.

## 9. Security

- Password and recovery code are never stored in plaintext, in logs, or in Room.
- Store the hash, salt, and iteration count in DataStore. Encrypt the stored blob with an AES-GCM key held in Android Keystore (do not claim Keystore hashes passwords).
- Constant-time comparison.
- Rate limiting with increasing delays after repeated failures (30s, 2m, 15m, 1h). Persist the failure count and lockout so process death and app restarts do not reset it. Document that clearing app data resets everything (this is an Android limitation, and the Accessibility Guard blocks access to Clear Data).
- Authentication required before: pausing or disabling filtering, removing user-added rules, editing rules, changing resolver, exporting/importing, changing the password, changing lock timeout, disabling Device Admin, disabling Accessibility Guard, uninstalling the app, accessing any guarded Android Settings screen.
- Authentication is NOT required for: adding a new blocking rule (adding blocks is safe).
- **Authentication NEVER grants:** removal or disabling of the adult content blocklist (once enabled, it is permanent).
- Import is treated as adding rules only. A file that would remove existing rules is rejected.
- Recovery flow: during setup, generate a 160-bit random code, show it once with a clear warning, require the user to confirm they saved it. To reset, the user enters the code; on success the user sets a new password and a **new** recovery code is issued. Explain in onboarding that losing both password and code means the only way to regain access is to perform a factory reset.
- Use `FLAG_SECURE` on password screens and exclude sensitive screens from recents thumbnails.
- Validate imported files: size limit, schema version, per-entry validation, no code execution, no unexpected fields trusted.

## 10. Protection and persistence

- Run the VPN as a foreground service with a persistent notification. Use foreground service type `specialUse` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "vpn"` and permission `FOREGROUND_SERVICE_SPECIAL_USE`.
- Handle `onRevoke()`: mark protection OFF, update the notification, show a clear in-app alert, post a high-priority notification ("⚠️ LockWeb VPN was disconnected"). Do not auto-restart in a tight loop; use a limited, backed-off retry only where Android permits.
- Boot receiver: on `BOOT_COMPLETED`, restart the VPN only if `VpnService.prepare()` indicates consent is already granted. Otherwise show a notification asking the user to reopen the app. Check current Android background-start restrictions.
- Use `VpnService.isAlwaysOn()` and `isLockdownEnabled()` (available inside the running service) to report whether Always-on VPN and "Block connections without VPN" are enabled. Provide a button to open Android VPN settings (through the password gate) and clear instructions to enable both. Never claim the app can force them on.
- Honest health model. "Protected" requires ALL of:
  1. VPN TUN established and read loop alive.
  2. AccessibilityGuardService is running and connected.
  3. A recent self-test passed (the app resolves an internal synthetic test name that the engine must answer with NXDOMAIN).
  4. Upstream resolver has answered successfully recently, or there have been no recent failures.
  Otherwise show Starting, Paused, PermissionRequired, Degraded(reason), or Error(reason) with a specific explanation.
- Private DNS detection: during onboarding, guide user to disable Private DNS. Use `ConnectivityManager` link properties to detect Private DNS in strict mode and warn. The Accessibility Guard blocks access to Private DNS settings to prevent re-enabling it.
- Chrome Secure DNS: during onboarding, guide user to disable Chrome's "Use secure DNS" setting. The Accessibility Guard blocks access to Chrome's privacy settings to prevent re-enabling it.
- Device Admin (guard): add a `DeviceAdminReceiver` with the minimal policy declaration. While it is active, Android blocks uninstalling the app via the launcher. The app offers an authenticated "Disable protection and allow uninstall" flow that calls `removeActiveAdmin()` after the password is entered. Implement `onDisableRequested()` with a warning message and `onDisabled()` to flag protection as weakened and notify the user.
- Be explicit in the UI and docs: Device Admin does not stop someone from deactivating it in Android Settings — but the Accessibility Guard does by requiring the password first. Document that Safe Mode and ADB remain as bypass vectors that require deliberate effort.

## 11. Screens

Build a **minimal, functional** UI. Material 3, light and dark themes. No placeholder buttons. Include loading, empty, error, and success states. Accessible contrast and touch targets. **Polish later; reliability first.**

**Onboarding:** Step-by-step setup wizard:
1. Welcome: brief explanation of what LockWeb does.
2. Password creation and confirmation (strength guidance).
3. Recovery code: shown once, must be confirmed ("I have saved this code").
4. Adult content blocklist: explanation and confirmation ("Once enabled, this cannot be turned off. Only a factory reset removes it."). Enable toggle.
5. Private DNS check: detect current setting, guide user to disable it if enabled.
6. Chrome Secure DNS: instructions to disable it manually.
7. VPN permission: trigger Android's consent dialog.
8. Accessibility Guard: guide user to enable the AccessibilityGuardService in Android Accessibility settings.
9. Device Admin: activate DeviceAdminReceiver.
10. "Display over other apps" (SYSTEM_ALERT_WINDOW) permission.
11. Battery optimization: guide user to exempt LockWeb from battery restrictions.
12. Final verification: check all components and show status (VPN running, Guard active, Device Admin active, Private DNS off). Separate "configured" from "actively protecting."

**Dashboard:** Protection status badge (Active / Degraded / Error — derived from live health checks, never hardcoded), blocked count today, "Add Website" button, blocklist summary. Loud banner when any protection layer is inactive.

**Blocklist:** User-added rules list with search, add (with normalization preview), remove (authenticated), edit (authenticated). Show adult blocklist as a separate, non-removable section with domain count. Duplicate detection, validation errors with correction hints, rule mode choice (default: hostname and subdomains).

**Protection Status:** VPN running, Accessibility Guard active, Device Admin active, Private DNS off, Chrome Secure DNS off, Always-on VPN status, Lockdown status. For each: green check / red warning, and a button to fix (through password gate if it opens Settings).

**Settings:** Theme toggle, auto-lock timeout, upstream resolver (authenticated), change password (authenticated), import/export blocklist (authenticated), "Disable protection and uninstall" flow (authenticated), app version.

**Password Gate:** Full-screen, uncancelable activity launched by the Accessibility Guard when a guarded Settings screen is detected. Uses FLAG_SECURE. Shows rate-limit countdown when locked out.

## 12. Privacy

- No account, no server, no analytics SDK, no ads.
- **One network exception:** the weekly OISD NSFW blocklist download over HTTPS. This is a one-way download only (no data is sent from the device except the standard HTTP GET request).
- All DNS traffic goes only to the user's chosen upstream resolver (default: Cloudflare).
- No logging of domains or browsing history. No packet payload storage. No HTTPS interception or custom certificates.
- The Accessibility Guard reads ONLY the foreground window's package name and activity class name. It does NOT read screen content, text fields, passwords, or messages.
- Write `docs/PRIVACY.md` in plain language documenting all of the above.

## 13. Testing and CI

- JVM unit tests in `:core-filter`: domain matcher, URL parsing, IDNA, packet parsing/building and checksums, DNS parse/build, NXDOMAIN builder, malformed-packet fuzz-style cases, cache TTL behavior, rule hot-swap, blocklist parsing and merging.
- Unit tests in `:app` for: password hashing and verification, rate-limit logic, recovery-code flow, import validation, blocklist update validation (reject empty/truncated lists).
- Do not add instrumentation tests that require an emulator in CI. VPN and Accessibility behavior cannot be reliably tested there.
- Write `docs/MANUAL_TEST_PLAN.md` for tests on a real phone (Android 14+). Include:
  - Blocked vs allowed sites, subdomains, incognito, other browsers, WebView-based apps.
  - Wi-Fi and mobile data, IPv4/IPv6.
  - Reboot persistence, rule changes taking effect.
  - **Anti-tamper tests:** attempt Force Stop, attempt Clear Data, attempt Device Admin deactivation, attempt VPN disconnect in Settings, attempt uninstall via Play Store — all should trigger password gate.
  - Attempt to enable Private DNS strict mode — should trigger password gate.
  - Attempt to enable Chrome Secure DNS — should trigger password gate.
  - VPN revoked by starting another VPN.
  - Adult blocklist: verify it cannot be removed after setup.
  - Safe Mode bypass (document as known limitation).
  Each row has a result column: Passed / Failed / Partially / Not tested / Not applicable. **Leave every result as "Not tested"** — you cannot run these.
- Include a battery/performance measurement guide using Android's own tooling (battery usage screen, `adb shell dumpsys batterystats`, Perfetto). Do not invent numbers.
- CI workflow runs unit tests and builds the debug APK.

## 14. Deliverables

- Source tree, Gradle config, wrapper, manifest with only needed permissions, committed debug keystore
- `:core-filter` and `:app` code, Compose UI, Room entities/DAOs/migrations, security code, VPN service, Accessibility Guard service, Device Admin receiver, boot receiver, blocklist manager
- Bundled OISD NSFW blocklist snapshot (raw asset)
- Unit tests
- `.github/workflows/build-apk.yml`
- `README.md` (what it is, honest limits, build via GitHub Actions, how to install the APK, how to activate protection step by step, how to uninstall)
- `docs/ARCHITECTURE.md`, `docs/VPN_DESIGN.md`, `docs/ANTI_TAMPER_GUIDE.md`, `docs/THREAT_MODEL.md`, `docs/PRIVACY.md`, `docs/MANUAL_TEST_PLAN.md`, `docs/KNOWN_LIMITATIONS.md`

## 15. Known limitations to document honestly

- DNS-only blocking does not stop direct-IP access, apps with hardcoded DNS, proxies, or third-party VPNs. Starting another VPN revokes this one.
- DoH and DoT bypass DNS filtering. LockWeb mitigates this by guiding the user to disable Private DNS and Chrome Secure DNS, and blocking access to those settings. But a technically savvy person could re-enable them via ADB or a different browser's settings.
- Hostnames can be hidden by ECH and QUIC, and this design does not inspect connection metadata.
- Blocking one domain does not block alternative domains or CDNs used by the same service.
- The adult content blocklist depends on the OISD community list. It may not cover every adult domain. New domains may appear between weekly updates.
- When a site is blocked, the browser shows a generic "This site can't be reached" error, not a branded LockWeb block page. (A custom block page requires a local HTTP server and TCP handling, which is out of scope for V1.)
- Device Admin blocks casual uninstall but can be deactivated in Android Settings — the Accessibility Guard requires the password first, but Safe Mode bypasses all third-party services.
- Clearing app data erases the password, rate-limit state, blocklist, and the permanent adult filter flag. The Accessibility Guard blocks access to Clear Data, but Safe Mode or ADB can bypass this.
- Always-on VPN and lockdown can only be enabled by the user in Android Settings (through the password gate).
- Boot restart depends on prior VPN consent and Android background-start rules.
- Safe Mode disables all third-party services (VPN, Accessibility Guard, Device Admin enforcement). This is an Android OS limitation that cannot be prevented without Device Owner mode (out of scope for V1).
- ADB commands from a computer can disable or uninstall LockWeb. This requires physical access to a computer with ADB set up.

## 16. Out of scope for version 1

Device Owner / managed-device mode (planned for V2), full-traffic forwarding (default route), SNI inspection, custom branded block page (requires local HTTP server), app blocking, scheduled blocking, accountability partner / shared approval, cloud sync, multi-device management, Play Store publishing, time-delay after password entry.

## 17. Future V2 features (documented for planning only)

- **Device Owner mode via ADB:** Step-by-step instructions to run `adb shell dpm set-device-owner com.lockweb.app/.LockWebDeviceAdminReceiver`. Enables `DISALLOW_SAFE_BOOT`, `DISALLOW_APPS_CONTROL`, `setUninstallBlocked()`, and `setAlwaysOnVpnPackage()`. Closes Safe Mode, Force Stop, and Clear Data bypass vectors at the OS kernel level.
- **Custom branded block page:** Local HTTP server on the TUN interface IP that serves "This site is blocked by LockWeb" for HTTP requests. HTTPS will still show a cert error (unavoidable without MITM).
- **Additional blocklist sources:** Let the user add custom blocklist URLs.
- **App blocking:** Monitor foreground app via Accessibility Service and block specific apps with a password gate overlay.

## 18. Definition of done

- Project builds in GitHub Actions and produces a debug APK artifact.
- All JVM unit tests pass in CI.
- VPN engine correctly blocks domains from both user rules and the adult blocklist.
- Accessibility Guard intercepts all guarded Settings screens and requires the master password.
- Adult content blocklist cannot be removed through the app after initial setup.
- Weekly blocklist update runs via WorkManager.
- Every screen in section 11 exists and is wired to real state.
- Docs are written and match what the code actually does.
- Anything not working or not verified is listed plainly in the README and in `docs/KNOWN_LIMITATIONS.md`.
