# DocScan Core Engine & Feature Wiring Plan (Android & iOS)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire every core on-device engine capability (`DocScanEngine`, `WorkQueue`, `DocumentPipeline`, `SuggestScanner`, `ImageEnhance`) and end-to-end product feature (Camera + Auto-Capture, Photo Picker / Files Import, 7-Day Library Suggest, Review/Edit Quad Crop & Enhance, OCR/NLP Pipeline, Search, Export/Print, Cloud Backup, StoreKit 2 / Play Billing) into both Android (`cam.docscan`) and iOS (`cam.docscan`) with zero hardcoded/stubbed data in production flows.

**Architecture:**
- **Android** already has `DocScanRuntime` + `DocumentPipeline` + Room wired to `saveDrafts()`. Remaining work is completing the missing vertical slices: real `MediaStore` 7-day `SuggestScanner`, live CameraX quad/blur/light analysis + auto-capture, Review/Edit real `BitmapEnhanceEngine` warp/rotate/filter commit, `SummaryEngine` ONNX/extractive gate, and removing `BillingStub.kt`.
- **iOS** has standalone `Engine/` modules (`DefaultDocScanEngine`, `VisionOcrEngine`, `ImageEnhance`, `WorkQueue`, `SuggestScanner`, etc.) and `CameraCaptureController`, `StoreKitManager`, `ExportServices`, `CloudBackupServices`, `DocScanStore` — but `DocScanAppState` still uses hardcoded strings and mock drafts. We port `DocumentPipeline` + `WorkJobHandler` to iOS, wire real `AVFoundation` capture + `PHPicker` import + `PhotoKit` 7-day suggest + `Review/Edit` image processing, and remove `StoreKitStub.swift` / `CaptureSessionStub.swift`.

**Tech Stack:**
- **Android:** Kotlin, Jetpack Compose, CameraX, ML Kit Text Recognition & LangID, ONNX Runtime (`1.22.0`), Room, Play Billing `7.1.1`, OkHttp, Firebase, AdMob.
- **iOS:** Swift 5, SwiftUI, AVFoundation, Vision (`VNRecognizeTextRequest`, `VNDetectDocumentSegmentationRequest`), CoreImage, NaturalLanguage, PhotoKit (`PHAsset`), PhotosUI (`PHPickerViewController`), StoreKit 2, PDFKit, Firebase, AdMob.

**Spec:** `PRODUCT.md`, `docs/features.md`, `docs/engines/contracts.md`, `docs/engines/resource-limits.md`, `docs/engines/model-manifest.md`, `docs/billing-quota.md`, `docs/ads-analytics.md`, `docs/screens/*`

## Global Constraints

- **Package / Bundle ID:** `cam.docscan`
- **UI strings:** English only.
- **No competitor binaries:** Never ship CamScanner `.so` binaries; all inference/processing runs in DocScan native code.
- **Bundled models only:** No R2 remote model downloads; generative summary falls back cleanly to extractive when weights are absent or `DeviceCapability.allowGenerative()` is false.
- **Free tier:** **5 page commits per UTC calendar day** across camera, import, and 7-day suggest, enforced authoritatively at **Save** (`POST /v1/quota/consume`). Re-edit of saved pages does **not** consume quota.
- **Privacy:** Never send document images, OCR text, summaries, or PII filenames to Firebase or the DocScan server.
- **Ads (Free only):** AdMob banner strictly on **Library**, **Search**, and **Doc Detail**; zero ads on Pro.

## Review Focus

1. **iOS `DocumentPipeline` parity & UI reactivity:** After Save, `commitDraftPages()` must return immediately to Library with the saved page visible, while `WorkQueue` steps (`OcrPage` → `LangPage` → `ClassifyPage` → `SummarizePage` → `WriteMetadata`) update `DocScanStore` and `@Published documents` without blocking the main thread.
2. **Review/Edit non-destructive vs committed bytes:** Dragging quad corners, rotating, or switching `EnhancePreset` in Review/Edit must render a downsampled live preview without OOM, and only write the final warped/rotated/enhanced JPEG to `FileStorageManager` when Save is tapped.
3. **7-Day Suggest permission & performance:** `MediaStore` (Android) and `PhotoKit` (iOS) queries must filter strictly to the last 7 days, request photo permission just-in-time on entering Library Suggest, and score thumbnails via `WorkQueue` at `Suggest` priority (yielding to `FreshScan`/`UserSave`).
4. **Re-edit quota & conditional re-OCR:** Re-editing a saved page must never call `consumeQuota`, and should only re-run `OcrPage`..`WriteMetadata` if image bytes, quad, rotation, or `EnhancePreset` changed.
5. **Offline & thermal/memory safety:** Batch imports/captures (10+ pages) must downsample previews (`<= 2048px`), persist full-res JPEGs to app-private storage, and throttle/skip generative summary when `DeviceCapability` reports low RAM/thermal pressure.

---

## Pipeline Model & Feature Wiring Matrix

| Pipeline Capability | Android Model / Engine | iOS Model / Engine | Feature & Screen Wiring |
|---|---|---|---|
| **Auto-Detect Doc (Live & Capture)** | `HeuristicDetectEngine` (`detect/docaligner.onnx` via ORT 1.22 if bundled + luminance-gradient border scan fallback) | `VisionOcrEngine.detectQuad` (`VNDetectDocumentSegmentationRequest`) | **Camera Capture**: live `ImageAnalysis` / `AVCaptureVideoDataOutput` (~3-4 fps) draws quad overlay, light/blur/stability guiding pill, triggers auto-capture when `autoCapture` ON & stable. **Import / Suggest**: pre-computes initial `Quad` for Review/Edit. |
| **Image Enhancement & Crop** | `BitmapEnhanceEngine`: `perspectiveWarp`, `rotate`, `ORIGINAL`, `COLOR`, `GRAY`, `BW` (integral adaptive threshold), `DOCUMENT` (contrast/sat), `DESHADOW` (background division) | `ImageEnhance` (CoreImage): `perspectiveWarp` (`CIPerspectiveCorrection`), `rotate`, `ORIGINAL`, `COLOR`, `GRAY`, `BW` (adaptive threshold), `DOCUMENT`, `DESHADOW` (Gaussian bg division) | **Review/Edit**: live downsampled preview (`<= 1024px`) when dragging quad corners, rotating, or switching `EnhancePreset`; full-res warp + rotate + enhance written to `FileStorageManager` on **Save** (before OCR). |
| **OCR + Auto-Orientation** | `MlKitOcrEngine` (ML Kit Latin + Chinese + Japanese + Korean + Devanagari fallback + upright angle re-OCR; `ocr/` ONNX slot ready for future Paddle/RapidOCR) | `VisionOcrEngine` (`VNRecognizeTextRequest` `.accurate`, `automaticallyDetectsLanguage = true`, upright angle re-OCR) | **Post-Save `WorkQueue` (`OcrPage`)**: runs on warped/enhanced image, persists `ocrText`, `ocrConfidence`, auto-title to Room / `DocScanStore`. Exposed in **Doc Detail** (Copy OCR), **Search** (FTS/LIKE), **Export** (invisible OCR text layer in PDF, text in DOCX). |
| **Language Detection** | `LangDetectEngine` (ML Kit `language-id:17.0.6` + Unicode script/Vietnamese/Latin heuristic) | `LangDetectEngine` (`NLLanguageRecognizer` + Unicode script/Vietnamese heuristic) | **Post-Save `WorkQueue` (`LangPage`)**: persists `ocrLang` (BCP-47) & `ocrLangConfidence` to DB & Doc Detail badge. |
| **Doc Classify & Tagging** | `DocClassifyEngine` + `HeuristicTagEngine` (Invoice, Receipt, Contract, ID, Tax, etc. + flags) | `DocClassifyEngine` + `HeuristicTagEngine` | **Post-Save `WorkQueue` (`ClassifyPage`)**: populates `docType`, `tags`, auto-rename suggestion (if `autoRename` pref ON), and **Library / Search** filter chips. |
| **Summary (Extractive + Gen)** | `SummaryEngine` (extractive scorer always-on; ORT generative slot `assets/models/summary/` gated by `DeviceCapability.allowGenerative()`) | `SummaryEngine` (extractive scorer always-on; CoreML/ORT generative slot `Resources/Models/summary/` gated by `DeviceCapability.allowGenerative()`) | **Post-Save `WorkQueue` (`SummarizePage` & `SummarizeBatch`)**: populates AI Summary card in **Doc Detail** & batch summary. |
| **Metadata Sidecar & EXIF** | `ImageMetadataWriter` (`.docscan.json` + Android `ExifInterface`) | `ImageMetadataWriter` (`.docscan.json` + `ImageIO` EXIF) | **Post-Save `WorkQueue` (`WriteMetadata`)**: embeds `PageNlpMetadata` on disk for local portability & backup. |
| **7-Day Library Suggest** | `MediaStoreSuggestProvider` + `SuggestScanner` on `WorkQueue` (`Suggest` priority = 40) | `PhotoKitSuggestProvider` + `SuggestScanner` on `WorkQueue` (`Suggest` priority = 40) | **Library Suggest**: scans last 7 days of device photos on-device, filters document-like items, imports selected photos into **Review/Edit**. |

---

## File Structure Overview

### Android (`android/app/src/main/java/cam/docscan/`)
- Create: `engine/suggest/MediaStoreSuggestProvider.kt` (real 7-day `MediaStore.Images` query + thumbnail loader)
- Modify: `capture/CameraXCaptureController.kt` (live quad detection + blur/luma stability metrics + auto-capture callback)
- Modify: `ui/screens/LibraryAndCaptureScreens.kt` (live quad overlay, guiding pill, auto-capture trigger, Review/Edit live enhance/warp preview)
- Modify: `ui/state/DocScanUiState.kt` (wire `MediaStoreSuggestProvider` + `SuggestScanner`, apply `perspectiveWarp`/`rotate`/`enhance` on draft commit, remove hardcoded `suggestCandidates`)
- Delete: `billing/BillingStub.kt` (replaced by `PlayBillingManager.kt`)

### iOS (`ios/DocScan/`)
- Create: `Engine/DocumentPipeline.swift` (mirrors Android `DocumentPipeline.kt` + `WorkJobHandler.kt`: stepped `WorkQueue` execution updating `DocScanStore`)
- Create: `Engine/PhotoKitSuggestProvider.swift` (last 7 days `PHAsset` query + thumbnail/full-res loader)
- Modify: `Engine/DefaultDocScanEngine.swift` (wire `WorkQueue` handler to `DocumentPipeline`)
- Modify: `Capture/CameraCaptureController.swift` (expose live `detectedQuad`, `lumaScore`, `stabilityScore`, and `autoCapture` trigger)
- Modify: `UI/State/DocScanAppState.swift` (replace all hardcoded capture/import/suggest/save strings with real `FileStorageManager`, `DocumentPipeline`, `PhotoKitSuggestProvider`, `ImageEnhance`, and `DocScanStore` sync; remove hardcoded sample docs in release/real flow)
- Modify: `UI/Screens/LibraryAndCaptureViews.swift` (wire shutter button to `captureController.capturePhoto()`, wire `PHPickerViewController` / `UIDocumentPickerViewController` import, wire Review/Edit live `ImageEnhance` preview + quad drag)
- Delete: `Billing/StoreKitStub.swift`, `Capture/CaptureSessionStub.swift`

---

### Task 1: iOS Core Pipeline (`DocumentPipeline.swift` + `DocScanStore` Reactive Updates)

**Files:**
- Create: `ios/DocScan/Engine/DocumentPipeline.swift`
- Modify: `ios/DocScan/Engine/DefaultDocScanEngine.swift`
- Modify: `ios/DocScan/Data/Local/DocScanStore.swift`
- Test: `ios/DocScanTests/DocumentPipelineTests.swift` (or unit test target)

**Interfaces:**
- Consumes: `DocScanEngineProtocol` (`DefaultDocScanEngine`), `WorkQueue`, `DocScanStore`, `FileStorageManager`.
- Produces: `DocumentPipeline.enqueuePage(pageId:imagePath:preset:priority:needEnhance:onUpdated:)`, `enqueueBatchSummary(batchId:pageIds:)`.

- [ ] **Step 1: Write failing test for `DocumentPipeline`**
  - Verify `enqueuePage` runs `ocr` → `lang` → `classify` → `summarize` → `metadata` in order and persists `ocrText`, `ocrLang`, `docType`, `tags`, `summary` into `DocScanStore`.
- [ ] **Step 2: Run test (`xcodebuild test` / Swift test) to verify failure**
- [ ] **Step 3: Implement `DocumentPipeline.swift`**
  - Implement `PageWorkContext` and stepped `WorkJob` execution (`enhance` -> `ocr` -> `lang` -> `classify` -> `summarize` -> `metadata`) using `DefaultDocScanEngine` and `DeviceCapability`.
  - Persist each completed step to `DocScanStore` and invoke an `@MainActor` callback so `DocScanAppState.documents` updates live.
- [ ] **Step 4: Wire `DefaultDocScanEngine` `WorkQueue` handler to `DocumentPipeline`**
- [ ] **Step 5: Run tests to verify pass & commit**

---

### Task 2: Real Camera Capture, Live Quad Overlay & Auto-Capture (Android & iOS)

**Files:**
- Modify: `android/app/src/main/java/cam/docscan/capture/CameraXCaptureController.kt`
- Modify: `android/app/src/main/java/cam/docscan/ui/screens/LibraryAndCaptureScreens.kt`
- Modify: `ios/DocScan/Capture/CameraCaptureController.swift`
- Modify: `ios/DocScan/UI/Screens/LibraryAndCaptureViews.swift`
- Delete: `ios/DocScan/Capture/CaptureSessionStub.swift`

**Interfaces:**
- Produces: `CaptureFrameGuidance(quad: Quad?, isLowLight: Boolean, isBlurry: Boolean, isStable: Boolean)`.

- [ ] **Step 1: Android — Upgrade `CameraXCaptureController` `ImageAnalysis`**
  - Downsample Y-plane / RGB frame at ~3-4 fps, run `HeuristicDetectEngine.detect()` for live quad, compute Laplacian/gradient variance for blur, luma average for lighting, and consecutive-frame quad IoU/delta for stability.
  - Emit `CaptureFrameGuidance` to `CameraCaptureScreen`; when `state.autoCapture && guidance.isStable && !guidance.isBlurry`, trigger `takePhoto()` with a 1.5s cooldown.
- [ ] **Step 2: iOS — Upgrade `CameraCaptureController` & wire `CameraCaptureView`**
  - In `captureOutput(_:didOutput:from:)`, throttle to ~4 fps, run `VNDetectDocumentSegmentationRequest` for `detectedQuad`, compute luma & stability, and trigger `capturePhoto()` when `state.autoCapture` is enabled and stable.
  - Replace `state.addCapturedPage()` in `LibraryAndCaptureViews.swift:507` with `Task { if let img = await captureController.capturePhoto() { state.addCapturedImage(img, quad: ...) } }`.
  - Delete `CaptureSessionStub.swift`.
- [ ] **Step 3: Verify build on Android (`./gradlew assembleDebug`) and iOS (`xcodebuild`) & commit**

---

### Task 3: Real Photo Picker / Files Import & Review/Edit Image Processing (Android & iOS)

**Files:**
- Modify: `android/app/src/main/java/cam/docscan/ui/state/DocScanUiState.kt`
- Modify: `android/app/src/main/java/cam/docscan/ui/screens/LibraryAndCaptureScreens.kt`
- Modify: `ios/DocScan/UI/State/DocScanAppState.swift`
- Modify: `ios/DocScan/UI/Screens/LibraryAndCaptureViews.swift`

**Interfaces:**
- Consumes: `BitmapEnhanceEngine` (Android), `ImageEnhance` (iOS), `FileStorageManager`.
- Produces: `addCapturedImage(...)`, `importImages(urls/images)`, `applyDraftEditsAndSave()`.

- [ ] **Step 1: iOS — Wire `PHPickerViewController` & `UIDocumentPickerViewController` (Images)**
  - Replace `importPhotos(count: Int)` dummy generator with real `importImages(_ images: [UIImage])` / `importImageURLs(_ urls: [URL])`: save original JPEG via `FileStorageManager`, run `engine.detectDocument(image:)` for initial quad, and open `.reviewEdit`.
- [ ] **Step 2: Android & iOS — Wire Review/Edit Live Preview & Commit Processing**
  - In Review/Edit screen, render the draft image with current `rotationDegrees`, `corners` quad overlay (draggable handles), and `enhanceMode` preview (downsampled <= 1024px for smooth UI).
  - On **Save** (`commitDraftPages`), before enqueuing `DocumentPipeline`:
    - If `corners` changed from full-frame or `rotationDegrees != 0` or `enhanceMode != ORIGINAL`, run `perspectiveWarp` + `rotate` + `enhance` on the full-res image and write the processed JPEG to `FileStorageManager` (keeping original or updating `imagePath`).
    - On re-edit (`editingDocumentId != nil`), only re-enqueue `DocumentPipeline` if `imagePath`, `corners`, `rotationDegrees`, or `enhanceMode` changed.
- [ ] **Step 3: Remove hardcoded initial sample documents in `DocScanAppState.swift` (or gate strictly behind `#if DEBUG` / empty-state)**
- [ ] **Step 4: Write unit tests for draft edit & re-edit conditional pipeline trigger; verify & commit**

---

### Task 4: Real 7-Day Library Suggest (`MediaStore` on Android, `PhotoKit` on iOS)

**Files:**
- Create: `android/app/src/main/java/cam/docscan/engine/suggest/MediaStoreSuggestProvider.kt`
- Modify: `android/app/src/main/java/cam/docscan/ui/state/DocScanUiState.kt`
- Create: `ios/DocScan/Engine/PhotoKitSuggestProvider.swift`
- Modify: `ios/DocScan/UI/State/DocScanAppState.swift`
- Modify: `ios/DocScan/UI/Screens/LibraryAndCaptureViews.swift`

**Interfaces:**
- Consumes: `SuggestScanner`, `WorkQueue` (`WorkPriority.SUGGEST` = 40), `FileStorageManager`.
- Produces: `refreshLibrarySuggestions()` populating `suggestCandidates` with real `mediaId`, thumbnail, timestamp, and `documentScore`.

- [ ] **Step 1: Write unit tests for 7-day date cutoff & `SuggestScanner` scoring filter**
- [ ] **Step 2: Android — Implement `MediaStoreSuggestProvider`**
  - Query `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` where `DATE_ADDED >= now - 7 days`, ordered descending, capped at 200 items.
  - Feed into `SuggestScanner` via `WorkQueue` (`WorkPriority.Suggest`).
  - Update `DocScanUiState.importSelectedSuggestions()` to copy selected `MediaStore` URIs to `FileStorageManager` and create real `DraftPage`s with `imagePath`.
- [ ] **Step 3: iOS — Implement `PhotoKitSuggestProvider`**
  - Request `.readWrite` / `.addOnly`? (`PHPhotoLibrary.requestAuthorization(for: .readWrite)` just-in-time when entering `.librarySuggest`).
  - Fetch `PHAsset` (`mediaType == .image`, `creationDate >= Date().addingTimeInterval(-7 * 86400)`), score thumbnails via `SuggestScanner`, and update `DocScanAppState.suggestCandidates`.
  - Update `importSelectedSuggestions()` to load full-res `UIImage` via `PHImageManager`, save to `FileStorageManager`, and open `.reviewEdit`.
- [ ] **Step 4: Remove hardcoded `suggestCandidates` ("Blue Bottle Coffee", etc.) from both `DocScanUiState.kt` and `DocScanAppState.swift`**
- [ ] **Step 5: Run tests & commit**

---

### Task 5: Engine Upgrades & Cleanup (Model Assets, Stubs Removal, End-to-End Verification)

**Files:**
- Modify: `android/app/src/main/java/cam/docscan/engine/summary/SummaryEngine.kt` (optional ORT generative model loader when `assets/models/summary/` has `.onnx` + tokenizer, preserving extractive fallback)
- Delete: `android/app/src/main/java/cam/docscan/billing/BillingStub.kt`
- Delete: `ios/DocScan/Billing/StoreKitStub.swift`
- Modify: `android/app/src/main/java/cam/docscan/ui/state/DocScanUiState.kt` & `ios/DocScan/UI/State/DocScanAppState.swift` (verify Export, Print, Backup, Billing, Delete Account & Data use zero stubs)

- [ ] **Step 1: Verify `BillingStub.kt` and `StoreKitStub.swift` have zero remaining references and delete them**
- [ ] **Step 2: Add ORT session check in `SummaryEngine.kt` (if `modelsDir/summary/model.onnx` exists and `DeviceCapability.allowGenerative()` is true, run ORT; otherwise extractive fallback)**
- [ ] **Step 3: Run full Android unit test suite (`./gradlew testDebugUnitTest`) and assemble (`./gradlew assembleDebug`)**
- [ ] **Step 4: Run iOS build & unit tests (`xcodebuild -project ios/DocScan.xcodeproj -scheme DocScan test`)**
- [ ] **Step 5: Commit & verify `git status` clean**
