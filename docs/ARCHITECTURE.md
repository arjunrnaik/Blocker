# LockWeb — Architecture & Dependency Decisions

## 1. Toolchain & Dependency Matrix

The following versions have been selected and tested for mutual compatibility:

| Component | Selected Version | Rationale & Compatibility |
|---|---|---|
| **JDK** | 17 (Temurin / OpenJDK 17) | Required minimum for Android Gradle Plugin 8.x. Fully supported by GitHub Actions `actions/setup-java`. |
| **Gradle** | 8.9 | Recommended and required minimum Gradle version for AGP 8.7.3. Stable release with full Kotlin DSL support. |
| **Android Gradle Plugin (AGP)** | 8.7.3 | Current stable release supporting `compileSdk = 35` and `minSdk = 34`. Fully compatible with JDK 17 and Gradle 8.9. |
| **Kotlin** | 2.0.21 | Stable Kotlin 2.0 compiler release. Bundles the new Compose Compiler Gradle plugin natively. |
| **Kotlin Compose Plugin** | 2.0.21 (`org.jetbrains.kotlin.plugin.compose`) | Since Kotlin 2.0.0, the Jetpack Compose compiler is integrated directly into the Kotlin compiler release train, eliminating separate Compose compiler version mismatches. |
| **KSP** | 2.0.21-1.0.28 | Matching Kotlin Symbol Processing plugin for Kotlin 2.0.21, required for Room annotation processing. |
| **Compose BOM** | 2024.10.01 | Stable Bill of Materials for Jetpack Compose, managing compatible versions of `ui`, `material3`, `runtime`, and tooling. |
| **Room** | 2.6.1 | Stable SQLite object mapping library with full coroutines and KSP support. |
| **DataStore Preferences** | 1.1.1 | Stable modern replacement for SharedPreferences with coroutine Flow integration. |
| **WorkManager** | 2.9.1 | Stable Android background task scheduler for periodic weekly blocklist updates. |
| **Coroutines** | 1.9.0 | Stable Kotlin asynchronous programming library matching Kotlin 2.0+. |
| **Core KTX** | 1.15.0 | Android KTX extensions for modern Android APIs. |
| **Activity Compose** | 1.9.3 | Stable integration between Android ComponentActivity and Jetpack Compose. |

---

## 2. Signing Configuration & Fixed Debug Keystore

### Why a Committed Debug Keystore is Required
In continuous integration environments (such as GitHub Actions runners), the default Android SDK `debug.keystore` is generated on the fly on every runner spin-up. This means:
1. Every CI build produces an APK signed with a different, freshly generated RSA key.
2. Android enforces signature consistency across app updates: if an app is installed on a device, Android strictly refuses to install an update signed with a different key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
3. For LockWeb, **Device Admin and Accessibility Guard permissions are active**. With Device Admin enabled, uninstalling the app to bypass a signature mismatch is blocked.
4. Therefore, committing a fixed, permanent debug keystore (`app/debug.keystore`) ensures that **every CI build from GitHub Actions shares the exact same cryptographic signature**, allowing seamless seamless updates on test devices without ever needing an uninstall.

*Note: Debug keystores contain no sensitive secrets (passwords and aliases are standard: `android`/`androiddebugkey`).*

---

## 3. Module Boundaries

1. **`:core-filter`**
   - **Language:** Pure Kotlin / JVM.
   - **Dependencies:** Standard library and JUnit only. Zero Android dependencies.
   - **Responsibility:** URL parsing, domain normalization (IDNA/RFC 1035), label-boundary domain matching, IPv4/IPv6/UDP packet codec with checksums, DNS wire protocol parsing/building (NXDOMAIN), and blocklist file ingestion.
   - **CI Benefit:** Fast, headless JVM unit test execution without Android emulators.

2. **`:app`**
   - **Language:** Kotlin / Android (minSdk 34, compileSdk 35).
   - **Dependencies:** Android Jetpack (Compose, Room, DataStore, WorkManager), `:core-filter`.
   - **Responsibility:** Android UI (Jetpack Compose), `VpnService` TUN interface loop, `AccessibilityGuardService` anti-tamper monitor, `DeviceAdminReceiver`, hardware-backed Keystore security, and persistent storage.
