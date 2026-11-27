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
)

class DocScanAppState(
    private val context: Context,
    private val runtime: DocScanRuntime,
    private val scope: CoroutineScope,
) {
    companion object {
        const val FREE_DAILY_QUOTA = 5
    }

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

    val suggestCandidates = mutableStateListOf(
        SuggestCandidate("sug-1", "Receipt", "Blue Bottle Coffee", "Yesterday · $14.20", 98, true, true),
        SuggestCandidate("sug-2", "Contract", "NDA Addendum P.1", "2 days ago · Legal", 94, true, true),
        SuggestCandidate("sug-3", "Invoice", "Studio Rig Invoice", "4 days ago · $480.00", 91, true, true),
        SuggestCandidate("sug-4", "Portrait", "Portrait photo", "3 days ago", 12, false, false),
        SuggestCandidate("sug-5", "Scene", "Coastline sunset", "5 days ago", 5, false, false),
    )

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
                val file = withContext(Dispatchers.IO) {
                    fileStorage.copyUriToPageFile(pageId, uri)
                }
                draftPages += DraftPage(
                    id = pageId,
                    title = "Imported Page ${idx + 1}",
                    sourceUri = uri,
                    imagePath = file?.absolutePath.orEmpty(),
                    enhanceMode = defaultEnhance,
                )
            }
            activeDraftIndex = 0
            currentRoute = DocScanRoute.REVIEW_EDIT
        }
    }

    fun importSelectedSuggestions() {
        val chosen = suggestCandidates.filter { it.selected }
        if (chosen.isEmpty()) return
        // Suggest media provider still stubbed — keep as review placeholders without fake OCR.
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
            enhanceMode = defaultEnhance,
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
                    if (pathChanged || first.enhanceMode != defaultEnhance) {
                        val bitmap = withContext(Dispatchers.IO) { decodeBitmap(first.imagePath) }
                        if (bitmap != null) {
                            val quad = cornersToQuad(first.corners, bitmap.width, bitmap.height)
                            runtime.pipeline.enqueuePage(
                                image = bitmap,
                                batchId = existing.batchId,
                                quad = quad,
                                preset = first.enhanceMode,
                                imagePath = first.imagePath.ifBlank { existing.imagePath },
                                source = existing.source,
                                sortIndex = existing.sortIndex,
                                pageId = existing.id,
                                freshScan = false,
                                needDetect = false,
                                needEnhance = first.enhanceMode != EnhancePreset.ORIGINAL,
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
                    val persistedPath = withContext(Dispatchers.IO) {
                        when {
                            draft.imagePath.isNotBlank() && File(draft.imagePath).exists() -> draft.imagePath
                            draft.sourceUri != null ->
                                fileStorage.copyUriToPageFile(draft.id, draft.sourceUri)?.absolutePath.orEmpty()
                            else -> draft.imagePath
                        }
                    }
                    if (persistedPath.isBlank()) return@forEachIndexed
                    val bitmap = withContext(Dispatchers.IO) { decodeBitmap(persistedPath) } ?: return@forEachIndexed
                    val quad = cornersToQuad(draft.corners, bitmap.width, bitmap.height)
                    val work = runtime.pipeline.enqueuePage(
                        image = bitmap,
                        batchId = batchId,
                        quad = quad,
                        preset = draft.enhanceMode,
                        imagePath = persistedPath,
                        source = if (draft.sourceUri != null) "import" else "camera",
                        sortIndex = idx,
                        pageId = draft.id,
                        freshScan = true,
                        needDetect = false,
                        needEnhance = draft.enhanceMode != EnhancePreset.ORIGINAL,
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
