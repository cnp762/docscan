# DocScan Mobile `.env` & Secrets Configuration Plan (Android & iOS)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate hardcoded build-time URLs, product IDs, ad unit IDs, OAuth client IDs, and signing/Firebase configs by wiring `.env` and `secrets/` cleanly into both Android (Gradle `BuildConfig` + manifest placeholders) and iOS (generated `.xcconfig` + `Info.plist` build settings + XcodeGen).

**Architecture:** Root `.env.example` defines all public template keys; local `.env` and `secrets/` remain gitignored. Android reads `.env` (fallback to `.env.example` for CI/debug builds) in `android/app/build.gradle.kts` and `android/keystore.properties` for release signing. iOS uses a small shell script (`ios/scripts/gen-xcconfig.sh`) invoked before `xcodegen generate` (or as an Xcode pre-action) to emit gitignored `ios/Config/Local.xcconfig`, which `ios/project.yml` and `ios/DocScan/Info.plist` reference via `$(...)` variables.

**Tech Stack:** Gradle Kotlin DSL (`android/app/build.gradle.kts`), XcodeGen (`ios/project.yml`), `.xcconfig`, `Info.plist`, bash/PowerShell.

**Spec:** `.env.example`, `.cursor/rules/10-security-secrets.mdc`, `.cursor/rules/20-android.mdc`, `.cursor/rules/30-ios.mdc`, `docs/ads-analytics.md`, `docs/store-upload.md`

## Global Constraints

- **Never commit** `.env`, `keystore.properties`, `.jks`/`.keystore`, `.p8`, `google-services.json`, `GoogleService-Info.plist`, or `Local.xcconfig`.
- **Bundle / Package ID:** `cam.docscan` (Android debug suffix `.debug` is allowed in `debug` buildType, matching existing `build.gradle.kts`).
- **UI strings:** English only.
- **No server secrets in mobile bundles:** Only `API_BASE_URL`, `PUBLIC_SITE_URL`, `PRODUCT_PRO_*`, `ADMOB_*`, and public OAuth Client IDs (`GOOGLE_OAUTH_CLIENT_ID_*`) may be embedded in mobile binaries. Never embed `JWT_SECRET`, `APPLE_PRIVATE_KEY_PATH`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, or `CF_API_TOKEN`.

## Review Focus

1. **Fresh clone without `.env`:** `./gradlew assembleDebug` and `xcodegen generate` must still succeed using `.env.example` / safe debug defaults (AdMob test IDs) without crashing Gradle or Xcode.
2. **Release build missing `keystore.properties` or AdMob/Firebase config:** Release builds must fail fast with a clear error if production signing or Firebase config is absent, rather than silently shipping debug/test IDs.
3. **Plist / BuildConfig escaping:** URLs (`https://api.docscan.cam`) in `.xcconfig` require `https:/$()/api.docscan.cam` (or `$(SLASH)`) because `//` starts an `.xcconfig` line comment! Failing to escape `//` truncates `API_BASE_URL` to `https:`.
4. **OAuth redirect schemes:** iOS `CFBundleURLTypes` needs the reversed Google OAuth client ID (`com.googleusercontent.apps.<id>`); Android needs `manifestPlaceholders` or Credential Manager config without leaking secrets.
5. **Gitignore completeness:** Verify `git status` shows zero tracked secrets after running both generators.

---

## File Structure

- Modify: `.gitignore` — ensure `ios/Config/Local.xcconfig`, `android/app/google-services.json`, `ios/DocScan/GoogleService-Info.plist`, `android/keystore.properties`, `*.jks`, `*.keystore` are ignored
- Modify: `.env.example` — add `ADMOB_APP_ID_ANDROID`, `ADMOB_BANNER_UNIT_ID_ANDROID`, `ADMOB_APP_ID_IOS`, `ADMOB_BANNER_UNIT_ID_IOS`, `GOOGLE_OAUTH_REVERSED_CLIENT_ID_IOS`
- Modify: `android/app/build.gradle.kts` — load root `.env` (fallback `.env.example`), populate `buildConfigField` & `manifestPlaceholders`, wire `signingConfigs.release` from `android/keystore.properties`
- Modify: `android/app/src/main/AndroidManifest.xml` — add `com.google.android.gms.ads.APPLICATION_ID` meta-data using `${admobAppId}`
- Create: `ios/scripts/gen-xcconfig.sh` — generate `ios/Config/Local.xcconfig` from `.env` (fallback `.env.example`), properly escaping `//` in URLs
- Create: `ios/Config/Default.xcconfig` — committed safe defaults (AdMob Google test IDs, `PRODUCT_PRO_*`)
- Modify: `ios/project.yml` — reference `Config/Default.xcconfig` / `Config/Local.xcconfig` and pass build settings
- Modify: `ios/DocScan/Info.plist` — replace hardcoded `API_BASE_URL` with `$(API_BASE_URL)`, add `GADApplicationIdentifier` (`$(ADMOB_APP_ID_IOS)`), `NSUserTrackingUsageDescription`, `CFBundleURLTypes` (`$(GOOGLE_OAUTH_REVERSED_CLIENT_ID_IOS)`), and `PRODUCT_PRO_*` keys
- Create: `ios/DocScan/Config/AppConfig.swift` — typed `Bundle.main.object(forInfoDictionaryKey:)` reader

---

### Task 1: Expand `.env.example` & Harden `.gitignore`

**Files:**
- Modify: `.env.example`
- Modify: `.gitignore`

- [ ] **Step 1: Add mobile ad & OAuth redirect variables to `.env.example`**

Append to `.env.example`:
```dotenv
# --- Mobile Ads (AdMob) ---
# Defaults below are Google-published test IDs for local dev; replace with real IDs in .env
ADMOB_APP_ID_ANDROID=ca-app-pub-3940256099942544~3347511713
ADMOB_BANNER_UNIT_ID_ANDROID=ca-app-pub-3940256099942544/6300978111
ADMOB_APP_ID_IOS=ca-app-pub-3940256099942544~1458002511
ADMOB_BANNER_UNIT_ID_IOS=ca-app-pub-3940256099942544/2934735716

# Reversed iOS OAuth client ID for CFBundleURLTypes (e.g. com.googleusercontent.apps.12345-abc)
GOOGLE_OAUTH_REVERSED_CLIENT_ID_IOS=
```

- [ ] **Step 2: Verify `.gitignore` covers all generated/secret mobile config files**

Ensure `.gitignore` includes:
```gitignore
.env
.env.*
!.env.example
secrets/*
!secrets/.gitkeep
!secrets/README.md
android/keystore.properties
android/app/google-services.json
ios/DocScan/GoogleService-Info.plist
ios/Config/Local.xcconfig
*.jks
*.keystore
*.p8
```

- [ ] **Step 3: Commit**

```bash
git add .env.example .gitignore
git commit -m "chore: add mobile AdMob/OAuth vars to .env.example and harden gitignore"
```

---

### Task 2: Android `.env` Loader, `BuildConfig`, `manifestPlaceholders` & Release Signing

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/README.md`

- [ ] **Step 1: Add `.env` parser helper in `android/app/build.gradle.kts`**

At the top of `android/app/build.gradle.kts`, load `rootProject.file("../.env")` if present, else `rootProject.file("../.env.example")` into a `Map<String, String>` (ignoring blank lines and `#` comments, stripping surrounding quotes).

- [ ] **Step 2: Wire `buildConfigField` and `manifestPlaceholders` from `.env`**

In `android.defaultConfig`:
- `API_BASE_URL` -> `env["API_BASE_URL"] ?: "https://api.docscan.cam"`
- `PUBLIC_SITE_URL` -> `env["PUBLIC_SITE_URL"] ?: "https://docscan.cam"`
- `PRODUCT_PRO_MONTHLY`, `PRODUCT_PRO_QUARTERLY`, `PRODUCT_PRO_YEARLY`
- `ADMOB_BANNER_UNIT_ID` -> `env["ADMOB_BANNER_UNIT_ID_ANDROID"]`
- `GOOGLE_OAUTH_CLIENT_ID_WEB` -> `env["GOOGLE_OAUTH_CLIENT_ID_WEB"] ?: ""` (needed for Android Credential Manager / Drive OAuth server-client-id if used)
- `manifestPlaceholders["admobAppId"] = env["ADMOB_APP_ID_ANDROID"] ?: "ca-app-pub-3940256099942544~3347511713"`

- [ ] **Step 3: Wire `signingConfigs.create("release")` from `android/keystore.properties`**

Load `rootProject.file("keystore.properties")` if it exists; assign `signingConfig = signingConfigs.getByName("release")` in `buildTypes.release` only when `keystore.properties` is present.

- [ ] **Step 4: Add AdMob `APPLICATION_ID` meta-data to `AndroidManifest.xml`**

Inside `<application>` in `android/app/src/main/AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${admobAppId}" />
```

- [ ] **Step 5: Verify Gradle build**

Run from `android/`: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` and `BuildConfig.java` contains the values from `.env` (or `.env.example`).

- [ ] **Step 6: Commit**

```bash
git add android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml android/README.md
git commit -m "build(android): load BuildConfig, AdMob manifest placeholder, and signing from .env"
```

---

### Task 3: iOS `.xcconfig` Generator, `Info.plist` & `AppConfig.swift`

**Files:**
- Create: `ios/Config/Default.xcconfig`
- Create: `ios/scripts/gen-xcconfig.sh`
- Modify: `ios/project.yml`
- Modify: `ios/DocScan/Info.plist`
- Create: `ios/DocScan/Config/AppConfig.swift`
- Modify: `ios/README.md`

- [ ] **Step 1: Create `ios/Config/Default.xcconfig` with safe defaults**

Remember `.xcconfig` comment rule: write `https:/$()/api.docscan.cam` so `//` is not parsed as a comment. Include `API_BASE_URL`, `PUBLIC_SITE_URL`, `PRODUCT_PRO_MONTHLY`, `PRODUCT_PRO_QUARTERLY`, `PRODUCT_PRO_YEARLY`, `ADMOB_APP_ID_IOS`, `ADMOB_BANNER_UNIT_ID_IOS`, `GOOGLE_OAUTH_CLIENT_ID_IOS`, `GOOGLE_OAUTH_REVERSED_CLIENT_ID_IOS`.

- [ ] **Step 2: Create `ios/scripts/gen-xcconfig.sh`**

Reads `../.env` (fallback `../.env.example`), escapes `://` -> `:/$()/` for `.xcconfig`, and writes `ios/Config/Local.xcconfig` starting with `#include "Default.xcconfig"`.

- [ ] **Step 3: Update `ios/project.yml` and `ios/DocScan/Info.plist`**

- In `ios/project.yml`: set `configFiles` for `Debug` and `Release` to `Config/Local.xcconfig` (or `Config/Default.xcconfig` when `Local.xcconfig` hasn't been generated yet).
- In `ios/DocScan/Info.plist`:
  - `API_BASE_URL` = `$(API_BASE_URL)`
  - `PUBLIC_SITE_URL` = `$(PUBLIC_SITE_URL)`
  - `ADMOB_BANNER_UNIT_ID` = `$(ADMOB_BANNER_UNIT_ID_IOS)`
  - `GADApplicationIdentifier` = `$(ADMOB_APP_ID_IOS)`
  - `GOOGLE_OAUTH_CLIENT_ID` = `$(GOOGLE_OAUTH_CLIENT_ID_IOS)`
  - `PRODUCT_PRO_MONTHLY`, `PRODUCT_PRO_QUARTERLY`, `PRODUCT_PRO_YEARLY`
  - `NSUserTrackingUsageDescription`: `"DocScan uses this to show relevant ads on the free plan. You can upgrade to Pro anytime for an ad-free experience."`
  - `CFBundleURLTypes` entry with `$(GOOGLE_OAUTH_REVERSED_CLIENT_ID_IOS)`

- [ ] **Step 4: Create `ios/DocScan/Config/AppConfig.swift`**

Expose typed static properties (`apiBaseURL: URL`, `adMobBannerUnitID: String`, `productIds: [String]`, `googleOAuthClientID: String?`) reading from `Bundle.main.object(forInfoDictionaryKey:)`.

- [ ] **Step 5: Verify script + XcodeGen (on macOS / CI)**

Run: `bash ios/scripts/gen-xcconfig.sh` and inspect `ios/Config/Local.xcconfig` to confirm `API_BASE_URL` is `https:/$()/api.docscan.cam` and `git status` shows `Local.xcconfig` is ignored.

- [ ] **Step 6: Commit**

```bash
git add ios/Config/Default.xcconfig ios/scripts/gen-xcconfig.sh ios/project.yml ios/DocScan/Info.plist ios/DocScan/Config/AppConfig.swift ios/README.md
git commit -m "build(ios): generate Local.xcconfig from .env and wire Info.plist AppConfig"
```
