# Rebase Plan: `mobile-ui` onto `origin/main` (and `origin/mobile-ui`)

> **For agentic workers:** Execute step-by-step. Do **not** run `git push --force` without `--force-with-lease`, and do **not** drop uncommitted `server/` changes without stashing first.

**Goal:** Safely rebase local `mobile-ui` (`756ead1`, containing `.env` config + mobile feature wiring) onto the latest `origin/mobile-ui` (`5131ccb`) and `origin/main` (`616693c` + any newly pushed `main` commits), resolving conflicts cleanly and verifying Android/iOS builds.

## Current Branch State (Snapshot)

- Local `mobile-ui`: `756ead1` (parent `c0c1944`)
- `origin/mobile-ui`: `5131ccb` (`fix(mobile-ui): 3-tab shell, Free ad slot, privacy copy` — 1 commit ahead of `c0c1944`)
- `origin/main`: `616693c` (when new code is pushed to `main`, `git fetch origin` will advance `origin/main`)
- Working tree: has uncommitted `server/` & `docs/superpowers/plans/2026-11-26-{ldp,server}-implementation.md` changes that must be stashed before rebasing.

---

### Task 1: Stash Uncommitted `server/` Work & Fetch Latest Remotes

- [ ] **Step 1: Stash untracked + modified `server/` & unrelated plan files**
  ```bash
  git stash push -u -m "wip: server & ldp plans before mobile-ui rebase" -- server/ docs/superpowers/plans/2026-11-26-ldp-implementation.md docs/superpowers/plans/2026-11-26-server-implementation.md
  ```
- [ ] **Step 2: Fetch latest `origin`**
  ```bash
  git fetch origin
  ```
- [ ] **Step 3: Inspect incoming commits on `origin/main` and `origin/mobile-ui`**
  ```bash
  git log --oneline HEAD..origin/main
  git log --oneline HEAD..origin/mobile-ui
  ```

---

### Task 2: Rebase `mobile-ui` onto `origin/mobile-ui` (`5131ccb`)

Since `5131ccb` is already on `origin/mobile-ui` and touches 10 UI/state files, rebase onto `origin/mobile-ui` first so `mobile-ui` has a linear history:

- [ ] **Step 1: Run rebase**
  ```bash
  git rebase origin/mobile-ui
  ```
- [ ] **Step 2: Expected conflict resolution rules**
  - `DocScanUiState.kt` & `DocScanAppState.swift`:
    - Keep `notificationsEnabled = false` default & privacy copy updates from `5131ccb`.
    - Keep `apiClient`, `fileStorage`, `cloudBackup`, `billingManager` / `storeKit`, `localStore`, `commitDraftPages()`, `deleteAccountAndData()`, `exportSelectedDocument()`, `restorePurchases()` from `756ead1`.
  - `DetailExportBackupPaywallSettingsScreens.kt` & `DetailExportBackupPaywallSettingsViews.swift`:
    - Keep `5131ccb` privacy copy / 3-tab adjustments.
    - Keep `756ead1` real `exportSelectedDocument`, `printSelectedDocument`, `purchasePlan`, `restorePurchases` handlers.
  - **AdMob Banner deduplication (`DocScanComponents.kt` / `DocScanTheme.swift` vs `DocScanAppRoot.kt` / `ContentView.swift`):**
    - `5131ccb` added Free AdMob banner placeholders in `DocScanComponents.kt` / `DocScanTheme.swift`, while `756ead1` mounted `AdMobBanner` / `AdMobBannerView` in `DocScanAppRoot.kt` / `ContentView.swift`.
    - Keep **one** banner slot per screen (either replace the placeholder in `DocScanComponents.kt` / `DocScanTheme.swift` with `AdMobBanner(isPro)` / `AdMobBannerView(isPro:)`, or remove the placeholder so `DocScanAppRoot.kt` / `ContentView.swift` owns the single banner strip above bottom nav).
- [ ] **Step 3: Continue rebase**
  ```bash
  git add <resolved-files>
  git rebase --continue
  ```

---

### Task 3: Rebase `mobile-ui` onto `origin/main`

Once `origin/main` has the new commits:

- [ ] **Step 1: Rebase onto `origin/main`**
  ```bash
  git rebase origin/main
  ```
- [ ] **Step 2: Resolve any `origin/main` conflicts**
  - **`.env.example` / `.gitignore`:** Union-merge keys from `origin/main` with mobile keys (`API_BASE_URL_PROD`, `API_BASE_URL_DEV`, `ADMOB_*`, `GOOGLE_OAUTH_REVERSED_CLIENT_ID_IOS`, `/export/`, `ios/Config/Local.xcconfig`).
  - **Server / API contract changes on `main`:** If `main` changed `/v1/devices/*`, `/v1/quota/*`, or `/v1/billing/*` schemas, update DTOs in `DocScanApiClient.kt` and `DocScanAPIClient.swift` + `DocScanApiClientTest.kt`.
  - **Docs / Engine changes on `main`:** Keep `main`'s engine/spec updates; ensure `DocScanUiState.kt` / `DocScanAppState.swift` still match `DocScanRuntime` / `DefaultDocScanEngine` signatures.

---

### Task 4: Verify & Push

- [ ] **Step 1: Verify Android unit tests + debug APK**
  ```bash
  cd android && ./gradlew.bat testDebugUnitTest assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL` (exit code 0).
- [ ] **Step 2: Verify iOS `.xcconfig` generation (on macOS)**
  ```bash
  cd ios && bash scripts/gen-xcconfig.sh && xcodegen generate
  ```
- [ ] **Step 3: Verify no secrets are tracked**
  ```bash
  git status --short
  ```
  Confirm `.env`, `Local.xcconfig`, `google-services.json`, `GoogleService-Info.plist`, `keystore.properties` are **not** tracked.
- [ ] **Step 4: Push `mobile-ui` safely & restore stashed `server/` work if needed**
  ```bash
  git push --force-with-lease origin mobile-ui
  git stash pop   # if you want the stashed server/ WIP back in working tree
  ```
