package cam.docscan.ui.state

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import cam.docscan.backup.CloudBackupManager
import cam.docscan.billing.PlayBillingManager
import cam.docscan.data.PageEntity
import cam.docscan.data.local.FileStorageManager
import cam.docscan.data.remote.DeviceIdentityStore
import cam.docscan.data.remote.DocScanApiClient
import cam.docscan.data.remote.QuotaConsumeResult
import cam.docscan.engine.DocScanRuntime
import cam.docscan.engine.api.EnhancePreset
import cam.docscan.engine.api.PointF
import cam.docscan.engine.api.Quad
import cam.docscan.export.ExportQualityLevel
import cam.docscan.export.ExportServices
import cam.docscan.telemetry.AnalyticsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

enum class DocScanRoute {
    ONBOARDING,
    CAMERA_PERMISSION,
    PHOTO_PERMISSION,
    HOME_LIBRARY,
    CAPTURE,
    LIBRARY_SUGGEST,
    REVIEW_EDIT,
    DOC_DETAIL,
    SEARCH_FILTER,
    EXPORT_SHARE,
    BACKUP,
    PAYWALL,
    SETTINGS,
}

enum class MainTab { LIBRARY, SCAN, IMPORT }

enum class CaptureMode { SINGLE, BATCH }

enum class ExportFormat(val label: String, val subtitle: String) {
    PDF("PDF Document (Searchable)", "Includes invisible OCR text layer for searching and copying"),
    DOCX("Microsoft Word (DOCX)", "Editable text layout with embedded scanned images"),
    IMAGES("Images (ZIP / JPEG)", "High-resolution color-corrected JPEG scans"),
}

enum class ExportQuality(val label: String, val estSize: String) {
    COMPACT("Compact", "450 KB"),
    STANDARD("Standard", "1.1 MB"),
    MAXIMUM("Maximum", "2.4 MB"),
}

enum class ProPlan(val id: String, val title: String, val price: String, val perMonth: String, val badge: String?) {
    YEARLY("pro_yearly", "Yearly Access", "$6 / year", "$0.50 / month · Billed $6 annually", "BEST VALUE · SAVE 50%"),
    QUARTERLY("pro_quarterly", "3 Months", "$2 / 3 mos", "$0.67 / month · Billed $2 every 3 months", "SAVE 33%"),
    MONTHLY("pro_monthly", "1 Month", "$1 / month", "Flexible monthly billing, cancel anytime", null),
}

data class DraftPage(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val sourceUri: Uri? = null,
    val imagePath: String = "",
    val enhanceMode: EnhancePreset = EnhancePreset.DOCUMENT,
    val rotationDegrees: Int = 0,
    val corners: List<Offset> = defaultQuad(),
    val ocrText: String = "",
) {
    companion object {
        fun defaultQuad(): List<Offset> = listOf(
            Offset(0.10f, 0.12f),
            Offset(0.90f, 0.12f),
            Offset(0.90f, 0.88f),
            Offset(0.10f, 0.88f),
        )
    }
}

data class SuggestCandidate(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val confidencePercent: Int,
    val isDocument: Boolean,
    val selected: Boolean,
    val mediaUri: String? = null,
)

class DocScanAppState(
    private val context: Context,
    private val runtime: DocScanRuntime,
    private val scope: CoroutineScope,
) {
    companion object {
        const val FREE_DAILY_QUOTA = 5
    }

    val modelsDir: File? get() = runtime.modelsDir

    var currentRoute by mutableStateOf(DocScanRoute.ONBOARDING)
    var activeTab by mutableStateOf(MainTab.LIBRARY)

    var isPro by mutableStateOf(false)
    var selectedPlan by mutableStateOf(ProPlan.YEARLY)
    var quotaUsedToday by mutableStateOf(0)
    var quotaUtcDay by mutableStateOf(LocalDate.now(ZoneOffset.UTC).toString())

    var cameraPermissionGranted by mutableStateOf(false)
    var photoPermissionGranted by mutableStateOf(false)

    var captureMode by mutableStateOf(CaptureMode.BATCH)
    var autoCaptureEnabled by mutableStateOf(true)
    var defaultEnhance by mutableStateOf(EnhancePreset.DOCUMENT)
    var autoRenameEnabled by mutableStateOf(true)
    var backgroundProcessingEnabled by mutableStateOf(true)
    var notificationsEnabled by mutableStateOf(false)

    val draftPages = mutableStateListOf<DraftPage>()
    var activeDraftIndex by mutableStateOf(0)
    var editingDocumentId by mutableStateOf<String?>(null)

    val documents = mutableStateListOf<PageEntity>()
    var selectedDocumentId by mutableStateOf<String?>(null)
    var selectedCategoryFilter by mutableStateOf("All")
    var searchQuery by mutableStateOf("")
    var searchOnlyTagged by mutableStateOf(false)

    val suggestCandidates = mutableStateListOf<SuggestCandidate>()
    var isScanningSuggestions by mutableStateOf(false)

    var googleDriveConnected by mutableStateOf(false)
    var googlePhotosConnected by mutableStateOf(false)
    var backupWifiOnly by mutableStateOf(true)
    var backupEntireLibrary by mutableStateOf(true)
    var lastSyncMessage by mutableStateOf("Not backed up yet")

    var exportFormat by mutableStateOf(ExportFormat.PDF)
    var exportQuality by mutableStateOf(ExportQuality.STANDARD)
    var exportEncryptPassword by mutableStateOf(false)
    var exportPassword by mutableStateOf("")

    var statusBanner by mutableStateOf<String?>(null)
    var isCapturing by mutableStateOf(false)

    private val identityStore = DeviceIdentityStore(context)
    val apiClient = DocScanApiClient(identity = identityStore)
    val fileStorage = FileStorageManager(context)
    val cloudBackup = CloudBackupManager(context)
    val billingManager = PlayBillingManager(context, apiClient) { pro, _ ->
        isPro = pro
        if (pro) {
            AnalyticsTracker.logEvent(context, "purchase_success", mapOf("is_pro" to true))
        }
    }

    init {
        isPro = billingManager.isCachedPro()
        googleDriveConnected = cloudBackup.getSignedInAccount() != null
        quotaUsedToday = 0
        scope.launch {
            runtime.repo.observeAll().collect { pages ->
                documents.clear()
                documents.addAll(pages)
                if (selectedDocumentId == null) {
                    selectedDocumentId = pages.firstOrNull()?.id
                } else if (pages.none { it.id == selectedDocumentId }) {
                    selectedDocumentId = pages.firstOrNull()?.id
                }
            }
        }
        scope.launch {
            apiClient.ensureRegistered()
            syncQuotaFromServer()
        }
        billingManager.startConnection()
    }

    fun syncQuotaFromServer() {
        scope.launch {
            apiClient.getQuotaToday().onSuccess { status ->
                quotaUsedToday = status.used
                quotaUtcDay = status.utcDay
                isPro = status.isPro || billingManager.isCachedPro()
            }
        }
    }

    val remainingFreeQuota: Int
        get() = (FREE_DAILY_QUOTA - quotaUsedToday).coerceAtLeast(0)

    fun refreshUtcQuotaIfNeeded() {
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        if (today != quotaUtcDay) {
            quotaUtcDay = today
            quotaUsedToday = 0
        }
    }

    fun selectedDocument(): PageEntity? =
        documents.firstOrNull { it.id == selectedDocumentId } ?: documents.firstOrNull()

    fun openDocument(id: String) {
        selectedDocumentId = id
        currentRoute = DocScanRoute.DOC_DETAIL
    }

    fun startNewCaptureSession() {
        editingDocumentId = null
        draftPages.clear()
        activeDraftIndex = 0
        currentRoute = if (cameraPermissionGranted) DocScanRoute.CAPTURE else DocScanRoute.CAMERA_PERMISSION
    }

    fun addCapturedPage(
        imagePath: String,
        corners: List<Offset> = DraftPage.defaultQuad(),
        title: String = "Scan ${draftPages.size + 1}",
        pageId: String = UUID.randomUUID().toString(),
    ) {
        if (imagePath.isBlank()) return
        draftPages += DraftPage(
            id = pageId,
            title = title,
            imagePath = imagePath,
            enhanceMode = defaultEnhance,
            corners = corners,
        )
        activeDraftIndex = draftPages.lastIndex
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        editingDocumentId = null
        draftPages.clear()
        scope.launch {
            uris.forEachIndexed { idx, uri ->
                val pageId = UUID.randomUUID().toString()
                val (file, detectedCorners) = withContext(Dispatchers.IO) {
                    val saved = fileStorage.copyUriToPageFile(pageId, uri)
                    val bmp = saved?.let { decodeBitmap(it.absolutePath) }
                    val quad = bmp?.let { runtime.engine.detectDocument(it) }
                    val corners = if (bmp != null && quad != null && bmp.width > 0 && bmp.height > 0) {
                        listOf(
                            Offset((quad.topLeft.x / bmp.width).coerceIn(0.02f, 0.98f), (quad.topLeft.y / bmp.height).coerceIn(0.02f, 0.98f)),
                            Offset((quad.topRight.x / bmp.width).coerceIn(0.02f, 0.98f), (quad.topRight.y / bmp.height).coerceIn(0.02f, 0.98f)),
                            Offset((quad.bottomRight.x / bmp.width).coerceIn(0.02f, 0.98f), (quad.bottomRight.y / bmp.height).coerceIn(0.02f, 0.98f)),
                            Offset((quad.bottomLeft.x / bmp.width).coerceIn(0.02f, 0.98f), (quad.bottomLeft.y / bmp.height).coerceIn(0.02f, 0.98f)),
                        )
                    } else {
                        DraftPage.defaultQuad()
                    }
                    bmp?.recycle()
                    saved to corners
                }
                draftPages += DraftPage(
                    id = pageId,
                    title = "Imported Page ${idx + 1}",
                    sourceUri = uri,
                    imagePath = file?.absolutePath.orEmpty(),
                    enhanceMode = defaultEnhance,
                    corners = detectedCorners,
                )
            }
            activeDraftIndex = 0
            currentRoute = DocScanRoute.REVIEW_EDIT
        }
    }

    fun refreshLibrarySuggestions() {
        if (!photoPermissionGranted || isScanningSuggestions) return
        isScanningSuggestions = true
        scope.launch {
            val provider = cam.docscan.engine.suggest.MediaStoreSuggestProvider(context)
            val scorer = cam.docscan.engine.suggest.SuggestScorer()
            val items = provider.loadChunk(cursor = null, limit = 60)
            val scored = withContext(Dispatchers.IO) {
                items.mapNotNull { item ->
                    val thumb = provider.loadThumb(item.mediaId) ?: return@mapNotNull null
                    val score = scorer.score(thumb)
                    thumb.recycle()
                    val pct = (score * 100).toInt().coerceIn(0, 100)
                    val isDoc = score >= 0.55f
                    val ageDays = ((System.currentTimeMillis() - item.takenAtMs) / 86_400_000L).coerceAtLeast(0L)
                    val ageLabel = when (ageDays) {
                        0L -> "Today"
                        1L -> "Yesterday"
                        else -> "$ageDays days ago"
                    }
                    SuggestCandidate(
                        id = item.mediaId,
                        category = if (isDoc) "Document" else "Photo",
                        title = if (isDoc) "Document candidate" else "Recent photo",
                        subtitle = "$ageLabel · ${item.width}×${item.height}",
                        confidencePercent = pct,
                        isDocument = isDoc,
                        selected = isDoc,
                        mediaUri = item.mediaId,
                    )
                }.sortedByDescending { it.confidencePercent }
            }
            suggestCandidates.clear()
            suggestCandidates.addAll(scored)
            isScanningSuggestions = false
        }
    }

    fun importSelectedSuggestions() {
        val chosen = suggestCandidates.filter { it.selected }
        if (chosen.isEmpty()) return
        val uris = chosen.mapNotNull { cand -> cand.mediaUri?.let { Uri.parse(it) } }
        if (uris.isNotEmpty()) {
            importUris(uris)
            return
        }
        editingDocumentId = null
        draftPages.clear()
        chosen.forEach { item ->
            draftPages += DraftPage(
                title = item.title,
                enhanceMode = defaultEnhance,
            )
        }
        activeDraftIndex = 0
        currentRoute = DocScanRoute.REVIEW_EDIT
    }

    fun editExistingDocument(doc: PageEntity) {
        editingDocumentId = doc.id
        draftPages.clear()
        draftPages += DraftPage(
            id = doc.id,
            title = doc.name,
            imagePath = doc.imagePath,
            enhanceMode = EnhancePreset.ORIGINAL,
            corners = listOf(
                Offset(0.03f, 0.03f),
                Offset(0.97f, 0.03f),
                Offset(0.97f, 0.97f),
                Offset(0.03f, 0.97f),
            ),
            ocrText = doc.ocrText.orEmpty(),
        )
        activeDraftIndex = 0
        currentRoute = DocScanRoute.REVIEW_EDIT
    }

    /**
     * Authoritative Save: quota gate then [DocumentPipeline.enqueuePage] for real OCR/NLP.
     */
    fun commitDraftPages(): Boolean {
        refreshUtcQuotaIfNeeded()
        val count = draftPages.size
        if (count == 0) return false

        val isReEdit = editingDocumentId != null
        if (!isReEdit && !isPro && quotaUsedToday + count > FREE_DAILY_QUOTA) {
            AnalyticsTracker.logEvent(context, "quota_hit", mapOf("quota_remaining" to remainingFreeQuota))
            currentRoute = DocScanRoute.PAYWALL
            return false
        }

        val snapshot = draftPages.toList()
        val targetEditId = editingDocumentId
        scope.launch {
            if (!isReEdit && !isPro) {
                when (val consume = apiClient.consumeQuota(count)) {
                    is QuotaConsumeResult.Allowed -> {
                        quotaUsedToday = consume.status.used
                        quotaUtcDay = consume.status.utcDay
                        isPro = consume.status.isPro
                    }
                    is QuotaConsumeResult.QuotaExceeded -> {
                        AnalyticsTracker.logEvent(context, "quota_hit", mapOf("quota_remaining" to 0))
                        currentRoute = DocScanRoute.PAYWALL
                        return@launch
                    }
                    is QuotaConsumeResult.NetworkError -> {
                        if (quotaUsedToday + count > FREE_DAILY_QUOTA) {
                            currentRoute = DocScanRoute.PAYWALL
                            return@launch
                        }
                        quotaUsedToday += count
                    }
                }
            }

            if (isReEdit && targetEditId != null) {
                val existing = documents.firstOrNull { it.id == targetEditId }
                val first = snapshot.first()
                if (existing != null) {
                    val pathChanged = first.imagePath.isNotBlank() && first.imagePath != existing.imagePath
                    val geomChanged = first.rotationDegrees != 0 || first.corners != DraftPage.defaultQuad()
                    if (pathChanged || first.enhanceMode != defaultEnhance || geomChanged) {
                        val processed = withContext(Dispatchers.IO) {
                            val raw = decodeBitmap(first.imagePath.ifBlank { existing.imagePath }) ?: return@withContext null
                            val out = applyDraftEdits(raw, first)
                            val saved = fileStorage.saveBitmap(existing.id, out)
                            if (out !== raw) raw.recycle()
                            out to saved.absolutePath
                        }
                        if (processed != null) {
                            runtime.pipeline.enqueuePage(
                                image = processed.first,
                                batchId = existing.batchId,
                                quad = null,
                                preset = EnhancePreset.ORIGINAL,
                                imagePath = processed.second,
                                source = existing.source,
                                sortIndex = existing.sortIndex,
                                pageId = existing.id,
                                freshScan = false,
                                needDetect = false,
                                needEnhance = false,
                            )
                        }
                    } else {
                        runtime.repo.insert(existing.copy(name = first.title))
                    }
                }
            } else {
                val batchId = if (snapshot.size > 1) UUID.randomUUID().toString() else null
                var firstId: String? = null
                snapshot.forEachIndexed { idx, draft ->
                    val prepared = withContext(Dispatchers.IO) {
                        val rawPath = when {
                            draft.imagePath.isNotBlank() && File(draft.imagePath).exists() -> draft.imagePath
                            draft.sourceUri != null ->
                                fileStorage.copyUriToPageFile(draft.id, draft.sourceUri)?.absolutePath.orEmpty()
                            else -> draft.imagePath
                        }
                        if (rawPath.isBlank()) return@withContext null
                        val raw = decodeBitmap(rawPath) ?: return@withContext null
                        val processed = applyDraftEdits(raw, draft)
                        val saved = fileStorage.saveBitmap(draft.id, processed)
                        if (processed !== raw) raw.recycle()
                        processed to saved.absolutePath
                    } ?: return@forEachIndexed
                    val work = runtime.pipeline.enqueuePage(
                        image = prepared.first,
                        batchId = batchId,
                        quad = null,
                        preset = EnhancePreset.ORIGINAL,
                        imagePath = prepared.second,
                        source = if (draft.sourceUri != null) "import" else "camera",
                        sortIndex = idx,
                        pageId = draft.id,
                        freshScan = true,
                        needDetect = false,
                        needEnhance = false,
                    )
                    if (firstId == null) firstId = work.pageId
                }
                selectedDocumentId = firstId
                val source = if (snapshot.firstOrNull()?.sourceUri != null) "import" else "camera"
                AnalyticsTracker.logEvent(
                    context,
                    "scan_save",
                    mapOf("n_pages" to snapshot.size, "source" to source, "is_pro" to isPro),
                )
            }
            draftPages.clear()
            editingDocumentId = null
            statusBanner = "Saved ${snapshot.size} page(s) — processing on device"
            currentRoute = DocScanRoute.HOME_LIBRARY
            activeTab = MainTab.LIBRARY
        }
        return true
    }

    fun addTagToSelected(tag: String) {
        val clean = tag.trim().lowercase().removePrefix("#")
        if (clean.isEmpty()) return
        val doc = selectedDocument() ?: return
        if (doc.tags.contains(clean)) return
        val updated = doc.copy(tags = doc.tags + clean)
        scope.launch { runtime.repo.insert(updated) }
    }

    fun removeTagFromSelected(tag: String) {
        val doc = selectedDocument() ?: return
        val updated = doc.copy(tags = doc.tags.filterNot { it == tag })
        scope.launch { runtime.repo.insert(updated) }
    }

    fun clearLocalLibrary() {
        selectedDocumentId = null
        scope.launch {
            runtime.repo.clearAll()
            fileStorage.clearAllFiles()
        }
        statusBanner = "Local library cleared"
    }

    fun deleteSelectedDocument() {
        val doc = selectedDocument() ?: return
        selectedDocumentId = null
        scope.launch {
            runtime.repo.deletePage(doc.id)
            if (doc.imagePath.isNotBlank()) {
                File(doc.imagePath).delete()
            }
        }
        statusBanner = "Document deleted"
        currentRoute = DocScanRoute.HOME_LIBRARY
    }

    fun renameSelectedDocument(newName: String) {
        val clean = newName.trim()
        val doc = selectedDocument() ?: return
        if (clean.isEmpty()) return
        scope.launch {
            runtime.repo.insert(doc.copy(name = clean))
        }
        statusBanner = "Renamed to $clean"
    }

    fun needsGoogleSignIn(): Boolean = cloudBackup.getSignedInAccount() == null

    fun buildGoogleSignInIntent(includePhotos: Boolean) = cloudBackup.buildSignInIntent(includePhotos)

    fun onGoogleSignInResult(granted: Boolean) {
        if (granted && cloudBackup.getSignedInAccount() != null) {
            statusBanner = "Google account connected for backup"
        } else {
            googleDriveConnected = false
            googlePhotosConnected = false
            statusBanner = "Google sign-in cancelled"
        }
    }

    fun syncBackupNow() {
        if (!googleDriveConnected && !googlePhotosConnected) {
            lastSyncMessage = "Connect Drive or Photos first"
            return
        }
        val targets = if (backupEntireLibrary) documents.toList() else listOfNotNull(selectedDocument())
        if (targets.isEmpty()) {
            lastSyncMessage = "No pages to sync"
            return
        }
        lastSyncMessage = "Syncing ${targets.size} page(s)..."
        scope.launch {
            var uploaded = 0
            var failed = 0
            for (page in targets) {
                val file = File(page.imagePath)
                if (!file.exists()) continue
                val displayName = "${page.name.ifBlank { "Scan" }}_${page.id.take(6)}.jpg"
                var ok = true
                if (googleDriveConnected) {
                    ok = ok && cloudBackup.uploadImageToDrive(file, displayName).isSuccess
                }
                if (googlePhotosConnected) {
                    ok = ok && cloudBackup.uploadImageToPhotos(file, displayName).isSuccess
                }
                if (ok) uploaded++ else failed++
            }
            lastSyncMessage = if (failed == 0) {
                "Synced $uploaded image(s) just now"
            } else {
                "Synced $uploaded, failed $failed (check Google sign-in)"
            }
            AnalyticsTracker.logEvent(context, "backup_sync", mapOf("uploaded" to uploaded, "failed" to failed))
        }
    }

    fun deleteAccountAndData() {
        clearLocalLibrary()
        isPro = false
        quotaUsedToday = 0
        googleDriveConnected = false
        googlePhotosConnected = false
        billingManager.clearLocalEntitlement()
        scope.launch {
            apiClient.deleteDeviceMe()
            cloudBackup.revokeAll()
        }
        statusBanner = "Device account & local data deleted"
    }

    fun exportSelectedDocument(activityContext: Context) {
        val doc = selectedDocument() ?: return
        val pages = documents.filter { it.id == doc.id || (doc.batchId != null && it.batchId == doc.batchId) }
        val q = when (exportQuality) {
            ExportQuality.COMPACT -> ExportQualityLevel.COMPACT
            ExportQuality.STANDARD -> ExportQualityLevel.STANDARD
            ExportQuality.MAXIMUM -> ExportQualityLevel.MAXIMUM
        }
        val (file, mime) = when (exportFormat) {
            ExportFormat.PDF -> ExportServices.exportSearchablePdf(fileStorage, doc.name, pages, q) to "application/pdf"
            ExportFormat.DOCX -> ExportServices.exportDocx(fileStorage, doc.name, pages, q) to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ExportFormat.IMAGES -> ExportServices.exportImagesZip(fileStorage, doc.name, pages, q) to "application/zip"
        }
        AnalyticsTracker.logEvent(context, "export_share", mapOf("format" to exportFormat.name.lowercase()))
        ExportServices.shareFile(activityContext, file, mime)
    }

    fun printSelectedDocument(activityContext: Context) {
        val doc = selectedDocument() ?: return
        val pages = documents.filter { it.id == doc.id || (doc.batchId != null && it.batchId == doc.batchId) }
        val pdf = ExportServices.exportSearchablePdf(fileStorage, doc.name, pages)
        AnalyticsTracker.logEvent(context, "export_share", mapOf("format" to "print"))
        ExportServices.printPdfFile(activityContext, pdf, doc.name)
    }

    fun purchasePlan(activity: Activity?, plan: ProPlan) {
        selectedPlan = plan
        if (activity != null && billingManager.launchPurchase(activity, plan.id)) {
            return
        }
        statusBanner = "Play Billing unavailable in this build"
    }

    fun restorePurchases() {
        scope.launch {
            val restored = billingManager.restorePurchases()
            isPro = restored
            if (restored) {
                AnalyticsTracker.logEvent(context, "restore_success")
                statusBanner = "DocScan Pro restored"
                currentRoute = DocScanRoute.HOME_LIBRARY
            } else {
                statusBanner = "No active Pro subscription found"
            }
        }
    }

    fun activatePro(plan: ProPlan) {
        selectedPlan = plan
        isPro = true
        statusBanner = "DocScan Pro active (${plan.title})"
        currentRoute = DocScanRoute.HOME_LIBRARY
    }

    private fun decodeBitmap(path: String): Bitmap? {
        if (path.isBlank()) return null
        return runCatching {
            BitmapFactory.Options().run {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(path, this)
                inSampleSize = calculateInSampleSize(this, 2048, 2048)
                inJustDecodeBounds = false
                BitmapFactory.decodeFile(path, this)
            }
        }.getOrNull()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (h, w) = options.outHeight to options.outWidth
        var sample = 1
        if (h > reqH || w > reqW) {
            var halfH = h / 2
            var halfW = w / 2
            while (halfH / sample >= reqH && halfW / sample >= reqW) {
                sample *= 2
            }
        }
        return sample
    }

    private suspend fun applyDraftEdits(raw: Bitmap, draft: DraftPage): Bitmap {
        val enhanceEngine = cam.docscan.engine.enhance.BitmapEnhanceEngine()
        var current = raw
        val isFullBleed = draft.corners.size == 4 &&
            draft.corners[0].x <= 0.04f && draft.corners[0].y <= 0.04f &&
            draft.corners[2].x >= 0.96f && draft.corners[2].y >= 0.96f
        if (!isFullBleed && draft.corners.size == 4) {
            val quad = cornersToQuad(draft.corners, current.width, current.height)
            val warped = enhanceEngine.perspectiveWarp(current, quad)
            if (warped !== current && current !== raw) current.recycle()
            current = warped
        }
        if (draft.rotationDegrees % 360 != 0) {
            val rotated = enhanceEngine.rotate(current, draft.rotationDegrees)
            if (rotated !== current && current !== raw) current.recycle()
            current = rotated
        }
        if (draft.enhanceMode != EnhancePreset.ORIGINAL) {
            val enhanced = runtime.engine.enhance(current, draft.enhanceMode)
            if (enhanced !== current && current !== raw) current.recycle()
            current = enhanced
        }
        return current
    }

    private fun cornersToQuad(corners: List<Offset>, width: Int, height: Int): Quad {
        val pts = corners.take(4).ifEmpty { DraftPage.defaultQuad() }
        fun px(o: Offset) = PointF(o.x.coerceIn(0f, 1f) * width, o.y.coerceIn(0f, 1f) * height)
        return Quad(
            topLeft = px(pts[0]),
            topRight = px(pts.getOrElse(1) { pts[0] }),
            bottomRight = px(pts.getOrElse(2) { pts[0] }),
            bottomLeft = px(pts.getOrElse(3) { pts[0] }),
        )
    }
}
