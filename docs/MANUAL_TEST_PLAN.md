# LockWeb Manual Test Plan & Physical Device Verification

This document specifies the end-to-end verification protocol to be executed on a physical Android device (Android 14+, API 34+).

> [!IMPORTANT]
> In accordance with `MASTER_PROMPT.md` Section 13, all result columns are initialized to **"Not tested"**. Tests must be executed manually on physical hardware.

---

## 1. Functional DNS Filtering Verification

| ID | Test Case | Steps to Execute | Expected Behavior | Result |
|---|---|---|---|---|
| **DNS-01** | Standard Hostname Block | Add rule `reddit.com`. Open Chrome and visit `reddit.com`. | Browser immediately shows `NXDOMAIN` (e.g. "This site can't be reached"). Block counter increments. | Not tested |
| **DNS-02** | Subdomain Blocking | With rule `reddit.com` in SUBDOMAINS mode, visit `old.reddit.com` and `www.reddit.com`. | Both subdomains are blocked with `NXDOMAIN`. | Not tested |
| **DNS-03** | Allowed Domain Pass-Through | Visit `wikipedia.org` or `google.com`. | Website loads normally with full speed. Block counter does not increment. | Not tested |
| **DNS-04** | Incognito / Private Browsing | Open an Incognito tab in Chrome and navigate to `reddit.com`. | Website is blocked identically to standard browsing tabs. | Not tested |
| **DNS-05** | Alternative Browsers | Open Mozilla Firefox or DuckDuckGo and navigate to `reddit.com`. | Website is blocked across all non-Chrome browsers. | Not tested |
| **DNS-06** | WebView-Based Applications | Open an app with an internal WebView (e.g., Reddit client, Twitter/X) loading `reddit.com`. | In-app webview fails to load blocked destination. | Not tested |
| **DNS-07** | Wi-Fi to Cellular Handoff | Connect to Wi-Fi, verify block. Turn off Wi-Fi (switch to LTE/5G). Refresh page. | Block persists seamlessly across network interface switches. | Not tested |
| **DNS-08** | IPv6 DNS Queries | Ensure cellular/Wi-Fi has IPv6 connectivity. Query an AAAA record for blocked host. | Synthetic IPv6 `NXDOMAIN` is returned. | Not tested |
| **DNS-09** | Live Rule Addition | Add `www.zedge.net` while browsing. Immediately visit `zedge.net`. | Rule is compiled and pushed instantly without restarting VPN. | Not tested |
| **DNS-10** | Live Rule Removal (Authenticated) | Tap Delete on `www.zedge.net`, enter master password. Visit `zedge.net`. | Rule is removed from Room DB; website loads normally. | Not tested |

---

## 2. Adult Content Permanence Verification

| ID | Test Case | Steps to Execute | Expected Behavior | Result |
|---|---|---|---|---|
| **NSFW-01** | OISD Adult Filtering | Enable Adult Content Filter during onboarding or settings. Visit known adult domains. | Immediate `NXDOMAIN` block. Adult list reflects 100k+ active domains. | Not tested |
| **NSFW-02** | Irreversible Lockout Test | Navigate to Blocklist / Settings. Attempt to find toggle or button to disable adult list. | No disable button or toggle exists in any screen. | Not tested |
| **NSFW-03** | Rule Export Exclusion | Go to Settings → Export Rules. Inspect the generated JSON. | JSON contains ONLY user-added custom rules. OISD list is never exported. | Not tested |
| **NSFW-04** | WorkManager Sync Verification | Check WorkManager periodic task status via ADB. | `OisdBlocklistUpdateWorker` is scheduled weekly with unmetered network constraint. | Not tested |

---

## 3. Anti-Tamper Guard & Self-Protection Verification

| ID | Test Case | Steps to Execute | Expected Behavior | Result |
|---|---|---|---|---|
| **TAMPER-01** | Launcher Uninstall Block | Long-press LockWeb app icon on the home screen/launcher. | Android disables, grays out, or removes the "Uninstall" option. | Not tested |
| **TAMPER-02** | App Info Force Stop Interception | Go to Android Settings → Apps → LockWeb. | LockWeb Password Gate immediately launches full-screen with `FLAG_SECURE`. | Not tested |
| **TAMPER-03** | App Info Clear Data Interception | Go to Android Settings → Apps → LockWeb → Storage. | Intercepted by Password Gate before "Clear Storage" can be tapped. | Not tested |
| **TAMPER-04** | Device Admin Deactivation | Go to Settings → Security → Device Admin apps → LockWeb. | Intercepted by Password Gate. Deactivation warning dialog is displayed. | Not tested |
| **TAMPER-05** | VPN Disconnect in Settings | Go to Settings → Network → VPN → LockWeb. | Intercepted by Password Gate before VPN toggle can be switched off. | Not tested |
| **TAMPER-06** | Accessibility Service Disabling | Go to Settings → Accessibility → LockWeb. | Intercepted by Password Gate. Cannot toggle off without password. | Not tested |
| **TAMPER-07** | Android Private DNS Interception | Go to Settings → Network → Private DNS. | Intercepted by Password Gate before DNS provider can be changed. | Not tested |
| **TAMPER-08** | Chrome Secure DNS Interception | Open Chrome → Settings → Privacy & Security → Use Secure DNS. | Intercepted by Password Gate before DoH can be enabled. | Not tested |
| **TAMPER-09** | Google Play Store Uninstall | Open Play Store → search LockWeb → view App Details. | Intercepted by Password Gate before "Uninstall" can be tapped. | Not tested |
| **TAMPER-10** | Password Gate Back/Cancel | On any intercepted screen, press system Back or tap "Cancel (Home)". | System immediately executes `sendHome()`, returning to phone desktop. | Not tested |
| **TAMPER-11** | 60-Second Admin Window | On an intercepted screen, enter the correct Master Password. | User is granted 60 seconds of unintercepted access to modify settings. | Not tested |

---

## 4. Password Security & Rate Limiting Verification

| ID | Test Case | Steps to Execute | Expected Behavior | Result |
|---|---|---|---|---|
| **AUTH-01** | Correct Password Entry | Enter valid master password in AuthGateDialog. | Action succeeds immediately. | Not tested |
| **AUTH-02** | Incorrect Password Warnings | Enter wrong password 1 to 4 times. | Displays counter: "Incorrect password. X attempts remaining before lockout." | Not tested |
| **AUTH-03** | Tier 1 Lockout (30s) | Enter wrong password 5 consecutive times. | Input locks for 30s with active countdown timer. | Not tested |
| **AUTH-04** | Process Death Lockout Persistence | During an active 30s lockout, force close LockWeb from Recents and reopen. | Lockout countdown continues counting from remaining seconds (persisted on disk). | Not tested |
| **AUTH-05** | Tier 2 Lockout Escalation | Fail once after Tier 1 expires. | Lockout escalates to 60s, then 300s, then 900s. | Not tested |
| **AUTH-06** | Emergency Recovery Code | In password dialog, tap "Use Recovery Code" and enter 40-character hex key. | Unlocks immediately and forces new master password creation. | Not tested |

---

## 5. Persistence & System Lifecycle Verification

| ID | Test Case | Steps to Execute | Expected Behavior | Result |
|---|---|---|---|---|
| **BOOT-01** | Device Reboot Auto-Start | Restart device with LockWeb active. Unlock phone. | `BootReceiver` resumes VPN filter and Anti-Tamper Guard automatically. | Not tested |
| **BOOT-02** | Rule SQLite Persistence | Add custom rules, clear recent tasks, reboot device. | All custom rules and blocked counts persist intact. | Not tested |
| **BOOT-03** | VPN Revocation Handling | While LockWeb is running, connect a secondary VPN (e.g. WireGuard, 1.1.1.1). | Status changes to `Degraded`, high-priority revocation notification is posted. | Not tested |

---

## 6. Battery & Performance Measurement Protocol

To ensure LockWeb adheres strictly to zero-battery-drain requirements (routing only port 53 UDP DNS), use Android's native diagnostic tools:

### Method A: Built-in Battery Usage UI
1. Charge device to 100%.
2. Use device normally for 24 hours with LockWeb active.
3. Open **Settings → Battery → Battery Usage**.
4. Verify LockWeb accounts for **< 1.0%** of total daily battery consumption.

### Method B: ADB Batterystats
Connect device via USB and run:
```bash
# Reset battery statistics
adb shell dumpsys batterystats --reset

# After several hours of use, extract report
adb shell dumpsys batterystats com.lockweb.app > batterystats.txt
```
Inspect `batterystats.txt` to confirm:
* Background CPU time is negligible (< 10 seconds per day).
* Network mobile radio wakeups are limited strictly to DNS resolution cycles.

### Method C: Perfetto System Tracing
1. Enable Developer Options → **System Tracing**.
2. Record trace while browsing 50 websites.
3. Verify TUN interface `packetCodec` processing completes in `< 1ms` per query with zero dropped buffers.
