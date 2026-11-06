# LockWeb — Phased Prompts for Antigravity

How to use:
1. Put `MASTER_PROMPT.md` in the repo root first, so every phase can refer to it.
2. Paste the **Shared preamble** at the start of every phase.
3. Run one phase at a time. Push to GitHub, wait for the Actions build, and only move on when CI is green.
4. If CI fails, use the **CI fix prompt** at the bottom.

---

## Shared preamble (paste at the start of every phase)

```
You are working on LockWeb, an Android self-control website blocker. Read MASTER_PROMPT.md in the repo root first and follow it exactly, especially the honesty rules, fixed product decisions, anti-tamper design, and architecture.

The user IS the adversary. LockWeb must resist impulsive bypass attempts.

Environment: I am not using Android Studio. Builds run in GitHub Actions. The Android SDK may not exist in this workspace. Do not claim anything compiled or ran unless you actually ran it. Do not guess dependency versions.

Work only on the phase below. Do not start later phases. When finished, give me:
1. A list of files created or changed.
2. What is implemented and what is not.
3. Anything you could not verify.
4. Exactly what I should check in the GitHub Actions run.
```

---

## Phase 0 — Project scaffold and CI

```
Phase 0: scaffold only.

Create a new Android project from scratch (empty repo):
- Gradle wrapper (gradlew, gradle/wrapper/*), Kotlin DSL, version catalog at gradle/libs.versions.toml.
- Two modules: :core-filter (pure Kotlin/JVM, no Android deps) and :app (Compose + Material 3, package com.lockweb.app, minSdk 34).
- Look up current stable, mutually compatible versions of AGP, Kotlin, Compose BOM, Room, DataStore, WorkManager, coroutines. Record them and the reasoning in docs/ARCHITECTURE.md.
- :app shows one placeholder screen that says "LockWeb" (this is the only placeholder allowed in the whole project, and it will be replaced).
- Commit a fixed debug keystore and configure signingConfigs.debug to use it, so every CI build has the same signature. Document why.
- Add .gitignore (but do NOT ignore the debug keystore).
- The CI workflow .github/workflows/build-apk.yml is provided by me and should already be in the repo. If it is missing, tell me and I will add it; do not write your own. If you believe it needs a change (for example the JDK version required by the AGP you chose), tell me exactly what and why instead of silently editing it.
- One trivial unit test in each module so CI proves tests run.

Acceptance: CI builds a debug APK and runs both tests.
```

---

## Phase 1 — Domain matcher (`:core-filter`)

```
Phase 1: domain normalization and matching, in :core-filter only. No Android code.

Implement per MASTER_PROMPT.md section 6:
- Input parsing: bare domain, www form, full URL with path/query/port/userinfo. Use a real URI parser.
- Normalization: lowercase, trailing dot, IDNA to ASCII, label/length validation.
- Rejections with specific error messages: IPv4/IPv6 literals, single-label names, bare public suffixes, malformed input.
- Rule modes: SUBDOMAINS (default) and EXACT.
- Compiled rule set: immutable, fast suffix lookup, label-boundary matching only. Duplicate and overlap detection.
- Blocklist file parser: parse a plain-text domain list (one domain per line, ignoring comments starting with # and blank lines). This is the format used by the OISD NSFW list.
- Blocklist merging: combine user rules and parsed blocklist domains into a single compiled rule set.
- Pure Kotlin API such as: parseInput(String): ParseResult, RuleSet.compile(userRules, blocklistDomains), RuleSet.matches(hostname): Boolean, BlocklistParser.parse(reader): List<String>.

Tests (JVM): every case in section 6, including reddit.com vs reddit.in / notreddit.com / reddit.com.example.org / xreddit.com, exact-only mode, Unicode and Punycode, trailing dots, uppercase, malformed URLs, IP literals, duplicates, overlapping rules. Also test blocklist parsing (comments, blank lines, malformed entries) and merging (user rule + blocklist rule for same domain, blocklist-only domain, user-only domain).

Acceptance: all tests pass in CI. List every edge case you tested.
```

---

## Phase 2 — Packet and DNS codec (`:core-filter`)

```
Phase 2: packet and DNS codec, in :core-filter only. No Android code.

Implement:
- IPv4 and IPv6 header parsing (reject fragments and unsupported extension-header chains safely), UDP parsing, and building IPv4/IPv6+UDP reply packets with correct lengths and checksums (IPv4 header checksum, UDP checksum including the IPv6 pseudo-header; handle the IPv4 UDP checksum correctly).
- DNS message parser: header, a single question (reject multiple questions safely), name decompression with loop and bounds protection, qtype/qclass, flags, truncation bit.
- DNS builders: NXDOMAIN response (same ID, question echoed, QR/RD/RA set, RCODE=3), SERVFAIL response.
- Response validation: transaction ID, question match, size limits.
- DNS cache with TTL handling and a hard size limit (pure Kotlin, injectable clock for tests).
- A pure function like DnsFilter.handleQuery(queryBytes, ruleSet): Decision, where Decision is Block(nxdomainBytes), Forward(queryBytes), or Drop(reason). Keep it free of I/O so it is fully testable.
- A blocked-query counter interface (increment-only, no domain names stored).

Tests (JVM): valid and malformed packets (truncated headers, bad lengths, compression loops, oversize names, zero questions, multiple questions), checksum correctness against known-good vectors, NXDOMAIN builder output re-parsed by your own parser, A/AAAA/HTTPS queries all blocked for a blocked name, cache TTL expiry, size-limit eviction, counter increments on block.

Acceptance: all tests pass in CI. State clearly that nothing here has run on a real device.
```

---

## Phase 3 — VPN service wiring (`:app`)

```
Phase 3: the Android VpnService, wired to :core-filter. No final UI yet, only a minimal debug screen with Start/Stop and a status line (it will be replaced later).

First write docs/VPN_DESIGN.md describing the DNS-only design in MASTER_PROMPT.md section 5, and the Android API facts you relied on (consent via VpnService.prepare, Builder routes/DNS, protect(), foreground service type for VPN apps on Android 14+). Verify these against official Android documentation and say what you verified.

Then implement:
- LockWebVpnService: Builder with private TUN address, fake resolver addresses (IPv4 and IPv6) added with addDnsServer, routes ONLY for those addresses (no default route), MTU set and documented.
- Foreground service with notification channel, foreground service type specialUse with PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "vpn", and FOREGROUND_SERVICE_SPECIAL_USE permission in the manifest.
- Read loop on a dedicated thread/coroutine, using DnsFilter from :core-filter. Forward allowed queries to the upstream resolver through a protect()-ed UDP socket with timeout, one retry, then SERVFAIL. TCP retry upstream on truncation. Bounded concurrency and buffers.
- Atomic rule hot-swap and cache flush on rule change (rules can come from an in-memory source for now; Room and blocklist file come in Phase 4).
- Anonymous blocked-query counter (increment-only, no domain names). Expose as StateFlow for the UI.
- Clean shutdown: close the TUN fd, sockets, and coroutine scopes. Implement onRevoke() and onDestroy().
- Honest health model per section 10: TUN alive, self-test with an internal synthetic name, recent upstream success. Expose it as a StateFlow of a sealed status type (Starting, Active, Paused, PermissionRequired, Degraded(reason), Error(reason)). Do not hardcode Active. Note: full "Protected" status also requires the Accessibility Guard (Phase 5), but for now just track VPN health.
- No logging of queried domain names anywhere.

Acceptance: CI builds. List every behaviour you could not test without a device, and give me a short device checklist for this phase (what I should try on my phone and what I should see).
```

---

## Phase 4 — Data, security, and blocklist manager (`:app`)

```
Phase 4: persistence, authentication, and blocklist management. Still no final UI.

Implement:
- Room: BlockRule entity (id, normalized host, mode, createdAt, optional note), DAO, database with an exported schema and a migration test setup. Never block the main thread. These are USER-ADDED rules only. The adult blocklist is stored as a flat file, not in Room.
- Repository that exposes user rules as Flow, loads the blocklist file, merges both sources, compiles a combined rule set, and pushes it to the VPN service.
- DataStore for settings (theme, lock timeout, upstream resolver) and for the permanent adult blocklist flag (adultBlocklistEnabled — once set to true, no code path ever sets it to false).
- Password: PBKDF2WithHmacSHA256, 600,000+ iterations, 16-byte salt, 256-bit key, constant-time compare. Store hash, salt, and iteration count in an AES-GCM blob protected by an Android Keystore key. Do not claim Keystore hashes anything.
- Rate limiting with persistent failure count and increasing lockout delays (30s, 2m, 15m, 1h) that survive process death.
- Recovery code: 160-bit random, shown once, only its PBKDF2 hash stored; reset flow issues a new password and a new recovery code.
- AuthManager with session state and auto-lock (default: lock immediately when the app leaves the foreground), plus a "requires authentication" gate API for sensitive actions.
- Blocklist manager:
  - Bundle a snapshot of the OISD NSFW list as a raw asset. On first run, copy it to internal storage.
  - WorkManager periodic task (weekly, constrained to Wi-Fi + not low battery) that downloads the latest OISD NSFW list from https://nsfw.oisd.nl/domainswild over HTTPS.
  - Validate downloaded list: reject if empty, reject if less than 50% the size of the current list (guard against truncated or malicious updates), validate format.
  - Atomically replace the cached file and trigger a rule set recompile + hot-swap into the VPN.
- Import/export of USER-ADDED rules only (not the adult blocklist) with strict validation (size limit, schema version, per-entry validation, no removal of existing rules via import).

Tests (JVM): hashing/verification, wrong password, rate-limit progression and persistence logic (inject the clock and storage), recovery code success/failure and rotation, import validation (oversize, bad schema, malformed entries, duplicates), blocklist download validation (empty list rejected, truncated list rejected, valid list accepted), adultBlocklistEnabled flag permanence (verify no code path sets it to false after initial enable).

Acceptance: CI green. Explain what depends on a real device (Keystore behaviour, WorkManager scheduling) and could not be tested.
```

---

## Phase 5 — Anti-Tamper Guard Engine (`:app`)

```
Phase 5: the AccessibilityGuardService — the critical self-control enforcement layer. Per MASTER_PROMPT.md section 7.

Implement:
- AccessibilityGuardService extending AccessibilityService.
  - In accessibility_service_config.xml: eventTypes = typeWindowStateChanged, feedbackType = feedbackGeneric, flags = flagDefault, canRetrieveWindowContent = false (we only need component names, not screen content).
  - On TYPE_WINDOW_STATE_CHANGED: read event.packageName and event.className.
  - Maintain a set of guarded (packageName, className) pairs covering:
    1. LockWeb's App Info in Android Settings (Force Stop, Clear Data, Uninstall)
    2. Device Admin settings screen
    3. VPN settings screen
    4. Private DNS settings screen
    5. Accessibility settings screen (self-protection)
    6. Google Play Store showing LockWeb's app detail or uninstall dialog
    7. Chrome's privacy/security settings (Secure DNS toggle)
  - Include known variants for Samsung One UI, Xiaomi MIUI, and stock AOSP. Document how to add more OEM variants.
  - When a guarded target is detected AND the user is not currently authenticated:
    1. Launch LockWebPasswordGateActivity with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP.
    2. If password is correct → set a 60-second authentication window (do not intercept again for 60 seconds).
    3. If dismissed or failed → performGlobalAction(GLOBAL_ACTION_HOME).

- LockWebPasswordGateActivity:
  - Full-screen, uses FLAG_SECURE.
  - Shows password input with rate-limit countdown when locked out.
  - On success: finish and allow the user to proceed to the previously guarded screen.
  - On back press or dismiss: perform GLOBAL_ACTION_HOME via the accessibility service.

- Manifest declarations:
  - AccessibilityGuardService with BIND_ACCESSIBILITY_SERVICE permission and meta-data pointing to accessibility_service_config.xml.
  - SYSTEM_ALERT_WINDOW permission for the overlay.

- Self-protection watchdog:
  - A periodic check (via WorkManager or a coroutine in the Application class) that verifies the AccessibilityGuardService is running.
  - If the service is not running, post a high-priority persistent notification: "⚠️ LockWeb protection is weakened — tap to re-enable."
  - Tapping the notification opens Accessibility settings for re-enablement.

- Integration with the health model:
  - Expose AccessibilityGuard status as a StateFlow<Boolean> (running or not).
  - Update the overall protection status: "Protected" now requires BOTH VPN Active AND Guard running.
  - Dashboard shows separate indicators for VPN and Guard.

Acceptance: CI builds. This phase CANNOT be tested without a real device. Provide a detailed device checklist:
  - Enable the AccessibilityGuardService in Android Settings.
  - Try to open Settings → Apps → LockWeb → verify password gate appears.
  - Try to open Settings → Device Admin → verify password gate appears.
  - Try to open Settings → VPN → verify password gate appears.
  - Try to open Settings → Private DNS → verify password gate appears.
  - Try to open Settings → Accessibility → verify password gate appears.
  - Enter correct password → verify 60-second window allows access.
  - Dismiss password gate → verify sent to home screen.
  - Force-kill the Accessibility Service → verify notification appears.
  Mark all as untested.
```

---

## Phase 6 — UI (`:app`)

```
Phase 6: the Compose UI per MASTER_PROMPT.md section 11. Replace the debug screen. Minimal and functional — polish later.

Screens:

1. Onboarding wizard (step-by-step):
   - Welcome screen
   - Password creation + confirmation
   - Recovery code display + confirmation
   - Adult content blocklist opt-in with permanence warning ("Once enabled, this cannot be turned off")
   - Private DNS check + guidance to disable
   - Chrome Secure DNS instructions
   - VPN consent dialog trigger
   - Accessibility Guard enablement (open Accessibility settings)
   - Device Admin activation
   - SYSTEM_ALERT_WINDOW permission
   - Battery optimization exemption guidance
   - Final verification: all components checked, status summary

2. Dashboard:
   - Protection status badge (Active / Starting / Degraded / Error) — derived from VPN health + Guard status, never hardcoded
   - Blocked count today (anonymous counter, no domain names)
   - Loud banner when ANY protection layer is inactive
   - "Add Website" button (no auth required)
   - Quick access to blocklist and settings

3. Blocklist screen:
   - Two sections: "Your Rules" (user-added, editable/removable with auth) and "Adult Content Filter" (permanent, shows domain count, not removable)
   - Add website: accepts domains/URLs, shows normalized hostname preview, duplicate detection, validation errors
   - Search
   - Rule mode choice (default: hostname + subdomains)

4. Protection Status screen:
   - VPN: running / stopped
   - Accessibility Guard: active / inactive
   - Device Admin: active / inactive
   - Private DNS: off / on (warning)
   - Always-on VPN: enabled / not enabled
   - Lockdown: enabled / not enabled
   - Each item: green check or red warning + fix button (opens Settings via password gate)

5. Settings screen:
   - Theme toggle (light / dark / system)
   - Auto-lock timeout
   - Upstream DNS resolver (authenticated)
   - Change password (authenticated)
   - Import/export user rules (authenticated)
   - "Disable all protection and uninstall" (authenticated, with confirmation warning)
   - App version

6. Password Gate Activity (already built in Phase 5, wire it into the UI flow)

Rules:
- Material 3, light and dark, minimal styling.
- Dashboard status comes ONLY from the VPN health + Guard StateFlows. Never show "Protected" unless BOTH are active.
- Sensitive actions go through the AuthManager gate.
- Password screens use FLAG_SECURE.
- Start VPN only after Android's consent dialog. No auto-start before consent.

Acceptance: CI green. Provide a screen-by-screen checklist for me to verify on my phone.
```

---

## Phase 7 — Protection, Device Admin, boot, Private DNS detection

```
Phase 7: persistence and hardening, per MASTER_PROMPT.md section 10.

Implement:
- DeviceAdminReceiver with the minimal policy declaration, an activation flow in the app, onDisableRequested() warning, onDisabled() handling that flags protection as weakened and posts a notification.
- Authenticated "Disable protection and allow uninstall" flow that calls removeActiveAdmin() only after the password is entered. This flow also stops the VPN and disables the Accessibility Guard.
- Boot receiver: restart the VPN after boot only if VpnService.prepare() shows consent was already granted; otherwise post a notification asking the user to reopen the app. Check current Android background-start restrictions and document what you found. Also check if AccessibilityGuardService auto-restarts after boot (it should, as Android restarts enabled accessibility services).
- Always-on and lockdown reporting using VpnService.isAlwaysOn() and isLockdownEnabled() from inside the service, with a button to open VPN settings (through the password gate) and written instructions to enable both.
- Private DNS detection (strict mode warning) via ConnectivityManager link properties. If detected, show a warning card on the Dashboard and Protection Status screen.
- Chrome Secure DNS: show a warning card with manual instructions. State plainly that DoH in browsers cannot be detected programmatically.
- Limited, backed-off restart logic after unexpected service death. No tight restart loops.
- VPN revocation handling: when onRevoke() is called, update status, show notification, and show banner on next app open.

Acceptance: CI green. Provide a device checklist:
  - Try to uninstall with Device Admin active (should fail with system message)
  - Deactivate Device Admin in Settings (should trigger password gate via Accessibility Guard)
  - Revoke VPN by starting another VPN (should trigger notification)
  - Reboot and verify VPN auto-restarts
  - Reboot and verify Accessibility Guard auto-restarts
  - Enable Private DNS strict mode (should trigger password gate via Accessibility Guard)
  - Use the authenticated "Disable and uninstall" flow
  Say what you expect to see for each and mark all as untested.
```

---

## Phase 8 — Documentation and final audit

```
Phase 8: documentation and a self-audit. No new features.

Write or finish:
- README.md: what LockWeb is, what it can and cannot do, how to build via GitHub Actions and download the APK artifact, how to install it, how to set up protection step by step (including Accessibility Guard enablement), how to update (note the fixed debug keystore), how to uninstall (enter password → disable all protection first).
- docs/ARCHITECTURE.md (update with final dependency versions and module structure)
- docs/VPN_DESIGN.md (update if anything changed during implementation)
- docs/ANTI_TAMPER_GUIDE.md: explain exactly what the Accessibility Guard protects, what it cannot protect against (Safe Mode, ADB), and how Device Owner mode (V2) would close remaining gaps.
- docs/THREAT_MODEL.md: every bypass vector, whether LockWeb mitigates it, and how.
- docs/PRIVACY.md: plain language explanation of what data LockWeb accesses and what it does not.
- docs/KNOWN_LIMITATIONS.md (all items in MASTER_PROMPT.md section 15).
- docs/MANUAL_TEST_PLAN.md with every result column set to "Not tested", including all anti-tamper tests. Plus the battery/performance measurement guide. Do not invent numbers.

Then audit the code against MASTER_PROMPT.md and report, as a table: each requirement, status (Done / Partial / Missing), and the file that implements it. Check specifically:
- No plaintext password anywhere, no domain names in any log or diagnostic export.
- "Protected" only appears when BOTH VPN health is Active AND Accessibility Guard is running.
- Every sensitive action is gated by authentication.
- The adult blocklist cannot be removed or disabled through any code path after initial setup.
- No hidden bypass of any kind.
- Docs match the real code.
- The Accessibility Guard covers all guarded targets listed in MASTER_PROMPT.md section 7.2.

Fix anything Missing that is within scope, and list the rest honestly.
```

---

## CI fix prompt (use whenever GitHub Actions fails)

```
The GitHub Actions build failed. Here is the log:

[PASTE THE FAILING PART OF THE LOG HERE — the first error, not just the last line]

Find the root cause, fix it with the smallest change, and explain it in two sentences. Do not refactor unrelated code. Do not weaken or delete tests to make CI pass. If the cause is a dependency or version conflict, check compatibility between AGP, Kotlin, Compose, Room, WorkManager, and the JDK before changing versions, and tell me what you changed and why.
```

## Device bug prompt (after you test on your phone)

```
I tested on my phone (Android [version], [phone model]). Observed behaviour:

[DESCRIBE WHAT YOU DID AND WHAT HAPPENED, including any status shown in the app and whether the password gate appeared when expected]

Do not guess. First list the possible causes, ranked by likelihood, based on how the code actually works. Then fix the most likely one and tell me how to verify it. Update docs/MANUAL_TEST_PLAN.md with the result I observed.
```

## Anti-tamper test prompt (after testing bypass attempts)

```
I tried to bypass LockWeb on my phone (Android [version], [phone model]):

What I tried: [DESCRIBE EXACTLY WHAT YOU DID — e.g. "I went to Settings → Apps → LockWeb and tried to tap Force Stop"]
What happened: [DESCRIBE WHAT ACTUALLY HAPPENED — e.g. "The password gate appeared" or "I was able to reach the Force Stop button without a password"]
Expected: [WHAT SHOULD HAVE HAPPENED]

If the guard failed to intercept, check the activity/package name detection logic. The OEM skin on my phone is [Samsung One UI / Xiaomi MIUI / Stock Android / Other]. Add the correct activity class name for this OEM if it is missing, and tell me how to find the correct class name using `adb shell dumpsys activity activities`.
```
