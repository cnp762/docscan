# DocScan Mobile Feature Wiring Implementation Plan (Android & iOS)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire all DocScan MVP features end-to-end on Android (`cam.docscan`) and iOS (`cam.docscan`) — replacing in-memory repositories and stubbed billing/capture/backup/ads/telemetry with real device persistence, server API integration, hardware camera/import/suggest, export (PDF/DOCX/Images/Print), cloud backup, StoreKit 2 / Play Billing, Firebase, and AdMob.

**Architecture:** Both apps already have UI screens (`ui/screens/`, `UI/Screens/`), state containers (`DocScanUiState.kt`, `DocScanAppState.swift`), and on-device processing engines (`engine/`, `Engine/`). We wire vertical slices underneath those state containers so UI flows stay aligned with `docs/screens/*`: (1) Network + Auth + Quota/Billing, (2) Local Persistence + File Storage, (3) Camera + Import + 7-Day Suggest + Review/Edit, (4) Export + Print/Share, (5) Cloud Backup, (6) Firebase Analytics/Crashlytics + AdMob Banner.

**Tech Stack:**
- **Android:** Kotlin, Jetpack Compose, Room + KSP, EncryptedSharedPreferences (or Tink/DataStore), OkHttp + kotlinx.serialization, CameraX, Photo Picker + MediaStore, Play Billing 7.1.1, Google Drive REST + Credential Manager, PdfDocument + Apache POI/minimal OOXML DOCX writer, Firebase Analytics + Crashlytics, Google Mobile Ads (AdMob).
- **iOS:** Swift 5, SwiftUI, SwiftData (or SQLite/GRDB) + FileManager + Keychain, URLSession, AVFoundation + Vision, PHPickerViewController + PhotoKit, StoreKit 2, PDFKit + minimal OOXML DOCX writer + UIPrintInteractionController, iCloud Drive (`FileManager.url(forUbiquityContainerIdentifier:)`) + Google Sign-In/Drive, Firebase iOS SDK, Google Mobile Ads + AppTrackingTransparency.

**Spec:** `PRODUCT.md`, `docs/features.md`, `docs/billing-quota.md`, `docs/ads-analytics.md`, `docs/data-model.md`, `docs/permissions.md`, `docs/storage-layout.md`, `docs/screens/*`

**Prerequisite:** Complete `docs/superpowers/plans/2026-11-26-mobile-env-config.md` first so `API_BASE_URL`, `PRODUCT_PRO_*`, `ADMOB_*`, and OAuth client IDs are injected via `.env`.

## Global Constraints

- **Package / Bundle ID:** `cam.docscan`
- **UI strings:** English only.
- **Free tier:** **5 page commits per UTC calendar day** across camera, import, and 7-day suggest. Authoritative check happens at **Save** via `POST /v1/quota/consume`. Insufficient quota returns **HTTP 402** → show Paywall.
- **Re-edit:** Editing an already-saved page does **not** consume quota.
- **Pro plans:** `pro_monthly` ($1), `pro_quarterly` ($2), `pro_yearly` ($6) — unlimited saves, **ad-free**.
- **Privacy (Firebase & Server):** Never send document images, OCR text, summaries, or PII filenames to Firebase or the DocScan server.
- **Ads (Free only):** AdMob banner strictly above bottom nav on **Library**, **Search**, and **Doc Detail** only. Never on Onboarding, Capture, Review/Edit, Paywall, Export/Share, Backup, or pickers. Pro builds never request or show ads.
- **Delete account & data (Settings):** Wipe local docs/files, revoke backup OAuth tokens, and call `DELETE /v1/devices/me`.

## Review Focus

1. **Quota race / offline save:** When online, Save must wait for `POST /v1/quota/consume` to succeed before committing pages to Room/SwiftData; when offline on Free, enforce a local UTC-day counter hard-capped at 5 so offline saves never bypass the limit.
2. **Reinstall + Restore Purchases:** Generating a new `device_id` on fresh install resets local state, so tapping "Restore Purchases" must query StoreKit 2 / Play Billing active entitlements, call `POST /v1/billing/verify` for the new `device_id`, and immediately unlock Pro + hide AdMob banners.
3. **Just-in-time permissions:** Never request `CAMERA` or `READ_MEDIA_IMAGES` / `NSPhotoLibraryUsageDescription` on app launch; request only when entering Capture or 7-day Suggest, and handle "Denied / Don't ask again" with a Settings deep-link.
4. **Memory & thermal safety on batch scans:** Processing 10+ pages in Review/Edit must downsample preview bitmaps, persist full-res JPEGs to app-private storage (`docs/storage-layout.md`), and run OCR/Summary through `WorkQueue` without OOM.
5. **AdMob Pro-entitlement race:** When a user upgrades to Pro on Paywall, immediately tear down any mounted banner view and reclaim vertical space without requiring an app restart.

---

## File Structure Overview

### Android (`android/app/src/main/java/cam/docscan/`)
- `data/local/` — Room `@Database`, `@Entity` (`PageEntity`, `BatchEntity`, `QuotaLocalEntity`), `@Dao`, `RoomPageRepository`
- `data/remote/` — `DocScanApiClient.kt` (OkHttp + kotlinx.serialization), `DeviceIdentityStore.kt` (EncryptedSharedPreferences UUID + JWT)
- `billing/` — Replace `BillingStub.kt` with `PlayBillingManager.kt` (`BillingClient` 7.1.1, query purchases, purchase flow, acknowledge, verify with server)
- `capture/` — `CameraXCaptureController.kt` (Preview + ImageAnalysis for quad detection/blur/light + ImageCapture)
- `export/` — `PdfExportService.kt` (Android `PdfDocument` + invisible OCR text layer), `DocxExportService.kt`, `ImageZipExportService.kt`, `PrintShareHelper.kt`
- `backup/` — `GoogleDriveBackupService.kt`, `GooglePhotosBackupService.kt`, `BackupOAuthManager.kt`
- `telemetry/` — `AnalyticsTracker.kt` (Firebase Analytics + Crashlytics wrapper enforcing zero-PII params), `AdMobBanner.kt`

### iOS (`ios/DocScan/`)
- `Data/Local/` — `DocScanStore.swift` (SwiftData or SQLite + `FileManager` storage layout), `KeychainStore.swift` (UUID + JWT)
- `Data/Remote/` — `DocScanAPIClient.swift` (`URLSession` async/await for `/v1/devices/*`, `/v1/quota/*`, `/v1/billing/*`)
- `Billing/` — Replace `StoreKitStub.swift` with `StoreKitManager.swift` (StoreKit 2 `Product.products`, `Transaction.updates`, `AppStore.sync()`, JWS verify)
- `Capture/` — Replace `CaptureSessionStub.swift` with `CameraCaptureController.swift` (`AVCaptureSession` + `AVCaptureVideoDataOutput` Vision quad/stability + `AVCapturePhotoOutput`)
- `Export/` — `PDFExportService.swift` (`PDFKit` + invisible OCR text), `DOCXExportService.swift`, `ImageExportService.swift`, `PrintShareHelper.swift`
- `Backup/` — `ICloudDriveBackupService.swift`, `GoogleDriveBackupService.swift`, `GooglePhotosBackupService.swift`
- `Telemetry/` — `AnalyticsTracker.swift` (Firebase Analytics + Crashlytics), `AdMobBannerView.swift` (`GADBannerView` + ATT prompt)

---

### Task 1: Device Identity, API Client & Quota/Billing Server Integration (Android & iOS)

**Files:**
- Android Create: `android/app/src/main/java/cam/docscan/data/remote/DeviceIdentityStore.kt`, `DocScanApiClient.kt`
- Android Modify: `android/app/build.gradle.kts` (add `okhttp`, `kotlinx-serialization-json`, `security-crypto`), `DocScanUiState.kt`
- iOS Create: `ios/DocScan/Data/Local/KeychainStore.swift`, `ios/DocScan/Data/Remote/DocScanAPIClient.swift`
- iOS Modify: `ios/DocScan/UI/State/DocScanAppState.swift`

**Interfaces:**
- Consumes: `BuildConfig.API_BASE_URL` (Android), `AppConfig.apiBaseURL` (iOS), Server endpoints `POST /v1/devices/register`, `DELETE /v1/devices/me`, `GET /v1/quota/today`, `POST /v1/quota/consume`, `POST /v1/billing/verify`, `GET /v1/billing/entitlement`.
- Produces: `registerIfNeeded()`, `consumeQuota(n: Int): QuotaResult`, `verifyBilling(store, productId, token)`, `deleteAccountData()`.

- [ ] **Step 1: Write unit tests for `DocScanApiClient` / `DocScanAPIClient` (mock HTTP responses for 200, 402 `quota_exceeded`, 401 token refresh)**
- [ ] **Step 2: Run tests to verify they fail**
- [ ] **Step 3: Implement `DeviceIdentityStore` (Android EncryptedSharedPreferences) & `KeychainStore` (iOS Keychain)**
- [ ] **Step 4: Implement `DocScanApiClient` (Android) & `DocScanAPIClient` (iOS) with `X-Device-Id` + `Authorization: Bearer <jwt>`**
- [ ] **Step 5: Wire `DocScanUiState.saveDrafts()` & `DocScanAppState.saveDrafts()` to call `consumeQuota(n)` before committing; on HTTP 402 navigate to Paywall**
- [ ] **Step 6: Wire Settings "Delete account & data" to `DELETE /v1/devices/me` + local wipe**
- [ ] **Step 7: Run tests & commit**

---

### Task 2: Real Store Billing (Play Billing 7 & StoreKit 2)

**Files:**
- Android Replace: `android/app/src/main/java/cam/docscan/billing/BillingStub.kt` -> `PlayBillingManager.kt`
- Android Modify: `DocScanUiState.kt`
- iOS Replace: `ios/DocScan/Billing/StoreKitStub.swift` -> `StoreKitManager.swift`
- iOS Modify: `DocScanAppState.swift`

- [ ] **Step 1: Implement `PlayBillingManager` (query `SUBS` for `BuildConfig.PRODUCT_PRO_*`, launch `BillingFlowParams`, acknowledge purchase, send `purchaseToken` to `POST /v1/billing/verify`, `queryPurchasesAsync` for Restore)**
- [ ] **Step 2: Implement `StoreKitManager` (`Product.products(for:)`, `product.purchase()`, `Transaction.updates` listener, `AppStore.sync()` for Restore, send JWS `verificationResult.jwsRepresentation` to `POST /v1/billing/verify`)**
- [ ] **Step 3: Cache `isPro` locally and update UI state immediately on purchase/restore**
- [ ] **Step 4: Test with Play Billing License Testing / Xcode StoreKit Configuration file (`.storekit`)**
- [ ] **Step 5: Commit**

---

### Task 3: Local Persistence (Room on Android, SwiftData + FileManager on iOS)

**Files:**
- Android Modify: `android/app/build.gradle.kts` (enable KSP + Room), `android/app/src/main/java/cam/docscan/data/AppDatabase.kt`
- Android Create: `android/app/src/main/java/cam/docscan/data/local/PageDao.kt`, `FileStorageManager.kt`
- iOS Create: `ios/DocScan/Data/Local/DocScanStore.swift`, `FileStorageManager.swift`
- Modify: `DocScanUiState.kt`, `DocScanAppState.swift`

- [ ] **Step 1: Write repository unit tests (insert page, upsert batch, FTS/substring search on name/ocrText/tags/summary, delete page, clear all)**
- [ ] **Step 2: Run tests to verify failure**
- [ ] **Step 3: Implement Room `@Database` + `RoomPageRepository` + FTS4/LIKE search on Android; SwiftData `@Model` + `FileStorageManager` on iOS following `docs/storage-layout.md`**
- [ ] **Step 4: Replace `InMemoryPageRepository` and in-memory arrays in `DocScanUiState` / `DocScanAppState`**
- [ ] **Step 5: Run tests to verify pass & commit**

---

### Task 4: Custom Camera, Photo Picker Import, 7-Day Suggest & Review/Edit Quad Crop

**Files:**
- Android Create: `android/app/src/main/java/cam/docscan/capture/CameraXCaptureController.kt`
- Android Modify: `LibraryAndCaptureScreens.kt`, `OnboardingAndPermissionsScreens.kt`, `DocScanUiState.kt`
- iOS Replace: `ios/DocScan/Capture/CaptureSessionStub.swift` -> `CameraCaptureController.swift`
- iOS Modify: `LibraryAndCaptureViews.swift`, `OnboardingAndPermissionsViews.swift`, `DocScanAppState.swift`

- [ ] **Step 1: Wire Android `CameraX` (`PreviewView`, `ImageAnalysis` connected to `HeuristicDetectEngine` + blur/light guidance + auto-capture stability timer, `ImageCapture`) and iOS `AVCaptureSession` (`VNDetectDocumentSegmentationRequest` + `AVCapturePhotoOutput`)**
- [ ] **Step 2: Wire Android `PickMultipleVisualMedia` (Photo Picker) & iOS `PHPickerViewController` for Import → `DraftPage` list → `REVIEW_EDIT`**
- [ ] **Step 3: Wire 7-day `SuggestScanner` on both platforms (just-in-time `READ_MEDIA_IMAGES` / `PHPhotoLibrary.requestAuthorization`)**
- [ ] **Step 4: Wire draggable 4-corner quad overlay + perspective warp + rotate + `EnhancePreset` (`Original`, `Color`, `Gray`, `Document`) in Review/Edit, plus re-edit from Doc Detail (no quota consumed)**
- [ ] **Step 5: Verify on device/emulator & commit**

---

### Task 5: Export (Searchable PDF, DOCX, Images/ZIP, Print, Share)

**Files:**
- Android Create: `android/app/src/main/java/cam/docscan/export/PdfExportService.kt`, `DocxExportService.kt`, `ImageExportService.kt`, `PrintShareHelper.kt`
- iOS Create: `ios/DocScan/Export/PDFExportService.swift`, `DOCXExportService.swift`, `ImageExportService.swift`, `PrintShareHelper.swift`
- Modify: `DetailExportBackupPaywallSettingsScreens.kt`, `DetailExportBackupPaywallSettingsViews.swift`

- [ ] **Step 1: Write unit tests verifying generated PDF contains selectable/searchable OCR text and generated DOCX is a valid ZIP with `word/document.xml` + embedded JPEG media**
- [ ] **Step 2: Implement searchable PDF (`PdfDocument` + transparent text draw on Android; `PDFKit` / `UIGraphicsPDFRenderer` + invisible text on iOS)**
- [ ] **Step 3: Implement minimal OOXML DOCX generator (`[Content_Types].xml`, `_rels/.rels`, `word/document.xml`, `word/media/*.jpg`) + JPEG/ZIP exporter**
- [ ] **Step 4: Wire `PrintManager` (Android) / `UIPrintInteractionController` (iOS) and native Share Sheet (`Intent.ACTION_SEND` / `UIActivityViewController`)**
- [ ] **Step 5: Run tests & commit**

---

### Task 6: Cloud Backup (Google Drive, Google Photos, iCloud Drive)

**Files:**
- Android Create: `android/app/src/main/java/cam/docscan/backup/BackupOAuthManager.kt`, `GoogleDriveBackupService.kt`, `GooglePhotosBackupService.kt`
- iOS Create: `ios/DocScan/Backup/ICloudDriveBackupService.swift`, `GoogleDriveBackupService.swift`, `GooglePhotosBackupService.swift`
- Modify: `DocScanUiState.kt`, `DocScanAppState.swift`, `DetailExportBackupPaywallSettings*`

- [ ] **Step 1: Implement Android Google OAuth (Drive `drive.file` + Photos `photoslibrary.appendonly` scopes) using `GOOGLE_OAUTH_CLIENT_ID_ANDROID` / `WEB` and upload JPEG/PDF to `DocScan/` folder**
- [ ] **Step 2: Implement iOS iCloud Drive (`FileManager.default.url(forUbiquityContainerIdentifier: nil)` + `DocScan/` directory) and Google OAuth via `ASWebAuthenticationSession` / GoogleSignIn using `GOOGLE_OAUTH_CLIENT_ID_IOS`**
- [ ] **Step 3: Wire disconnect/revoke OAuth in Backup & Settings (`Delete account & data` revokes tokens)**
- [ ] **Step 4: Test upload + disconnect flows & commit**

---

### Task 7: Firebase Analytics, Crashlytics & AdMob Banner (Free Only)

**Files:**
- Android Modify: `android/build.gradle.kts`, `android/app/build.gradle.kts` (Google Services, Crashlytics, `firebase-analytics`, `firebase-crashlytics`, `play-services-ads`)
- Android Create: `android/app/src/main/java/cam/docscan/telemetry/AnalyticsTracker.kt`, `AdMobBanner.kt`
- iOS Modify: `ios/project.yml` (add SPM packages: `firebase-ios-sdk`, `GoogleMobileAds`), `ios/DocScan/DocScanApp.swift`
- iOS Create: `ios/DocScan/Telemetry/AnalyticsTracker.swift`, `AdMobBannerView.swift`

- [ ] **Step 1: Configure `AnalyticsTracker` with strict param allowlist (`source`, `n_pages`, `is_pro`, `quota_remaining`, `trigger`, `format`) and core events from `docs/ads-analytics.md` (`onboarding_complete`, `scan_save`, `quota_hit`, `paywall_view`, `purchase_success`, `purchase_fail`, `restore_success`, `ad_impression`, `ad_click`, `export_share`)**
- [ ] **Step 2: Write unit test asserting `AnalyticsTracker` strips/rejects any disallowed keys (`ocr_text`, `image_path`, `summary`, `filename`)**
- [ ] **Step 3: Mount `AdMobBanner` strictly above bottom nav on `HOME_LIBRARY`, `SEARCH_FILTER`, `DOC_DETAIL` when `!isPro`; fail-soft (`0.dp` height) on load error; request ATT on iOS before loading personalized ads**
- [ ] **Step 4: Verify Pro toggle immediately removes banner and reclaims vertical space**
- [ ] **Step 5: Run tests & commit**
