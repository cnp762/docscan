package cam.docscan.ui.state

import android.app.Activity
import android.content.Context
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
import cam.docscan.engine.api.OcrPage
import cam.docscan.engine.api.SummaryMode
import cam.docscan.engine.classify.DocClassifyEngine
import cam.docscan.engine.lang.LangDetectEngine
import cam.docscan.engine.summary.SummaryEngine
import cam.docscan.export.ExportQualityLevel
import cam.docscan.export.ExportServices
import cam.docscan.telemetry.AnalyticsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    var quotaUsedToday by mutableStateOf(3)
    var quotaUtcDay by mutableStateOf(LocalDate.now(ZoneOffset.UTC).toString())

    var cameraPermissionGranted by mutableStateOf(false)
    var photoPermissionGranted by mutableStateOf(false)

    // Scan & Review state
    var captureMode by mutableStateOf(CaptureMode.BATCH)
    var autoCaptureEnabled by mutableStateOf(true)
    var defaultEnhance by mutableStateOf(EnhancePreset.DOCUMENT)
    var autoRenameEnabled by mutableStateOf(true)
    var backgroundProcessingEnabled by mutableStateOf(true)
    var notificationsEnabled by mutableStateOf(false)

    val draftPages = mutableStateListOf<DraftPage>()
    var activeDraftIndex by mutableStateOf(0)
    var editingDocumentId by mutableStateOf<String?>(null)

    // Library & Detail state
    val documents = mutableStateListOf<PageEntity>()
    var selectedDocumentId by mutableStateOf<String?>(null)
    var selectedCategoryFilter by mutableStateOf("All")
    var searchQuery by mutableStateOf("")
    var searchOnlyTagged by mutableStateOf(false)

    // Suggest state
    val suggestCandidates = mutableStateListOf(
        SuggestCandidate("sug-1", "Receipt", "Blue Bottle Coffee", "Yesterday · $14.20", 98, true, true),
        SuggestCandidate("sug-2", "Contract", "NDA Addendum P.1", "2 days ago · Legal", 94, true, true),
        SuggestCandidate("sug-3", "Invoice", "Studio Rig Invoice", "4 days ago · $480.00", 91, true, true),
        SuggestCandidate("sug-4", "Portrait", "Portrait photo", "3 days ago", 12, false, false),
        SuggestCandidate("sug-5", "Scene", "Coastline sunset", "5 days ago", 5, false, false),
    )

    // Backup state (Android: Google Drive + Google Photos only; images only)
    var googleDriveConnected by mutableStateOf(false)
    var googlePhotosConnected by mutableStateOf(false)
    var backupWifiOnly by mutableStateOf(true)
    var backupEntireLibrary by mutableStateOf(true)
    var lastSyncMessage by mutableStateOf("Not backed up yet")

    // Export state
    var exportFormat by mutableStateOf(ExportFormat.PDF)
    var exportQuality by mutableStateOf(ExportQuality.STANDARD)
    var exportEncryptPassword by mutableStateOf(false)
    var exportPassword by mutableStateOf("")

    var statusBanner by mutableStateOf<String?>(null)

    private val langEngine = LangDetectEngine()
    private val classifyEngine = DocClassifyEngine()
    private val summaryEngine = SummaryEngine()

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
            val saved = runtime.repo.all()
            if (saved.isNotEmpty()) {
                documents.clear()
                documents.addAll(saved)
            } else {
                seedInitialDocuments()
            }
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

    fun addCapturedPage(title: String = "Scan ${draftPages.size + 1}") {
        draftPages += DraftPage(
            title = title,
            enhanceMode = defaultEnhance,
            ocrText = "DOCSCAN CAPTURE #${draftPages.size + 1}\nProcessed locally on-device.",
        )
        activeDraftIndex = draftPages.lastIndex
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        editingDocumentId = null
        draftPages.clear()
        uris.forEachIndexed { idx, uri ->
            draftPages += DraftPage(
                title = "Imported Page ${idx + 1}",
                sourceUri = uri,
                imagePath = uri.toString(),
                enhanceMode = defaultEnhance,
                ocrText = "Imported image ${uri.lastPathSegment ?: idx + 1}\nReady for perspective crop & OCR.",
            )
        }
        activeDraftIndex = 0
        currentRoute = DocScanRoute.REVIEW_EDIT
    }

    fun importSelectedSuggestions() {
        val chosen = suggestCandidates.filter { it.selected }
        if (chosen.isEmpty()) return
        editingDocumentId = null
        draftPages.clear()
        chosen.forEach { item ->
            draftPages += DraftPage(
                title = item.title,
                enhanceMode = defaultEnhance,
                ocrText = "${item.category}: ${item.title}\n${item.subtitle}",
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
     * Authoritative Save step: enforces UTC daily quota for new pages via server POST /v1/quota/consume
     * (re-edits are free). Returns true if save flow started, false if blocked locally by free quota.
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
                    val updated = existing.copy(
                        name = first.title,
                        ocrText = first.ocrText.ifBlank { existing.ocrText },
                    )
                    runtime.repo.insert(updated)
                    val idx = documents.indexOfFirst { it.id == targetEditId }
                    if (idx >= 0) documents[idx] = updated
                }
            } else {
                val batchId = if (snapshot.size > 1) UUID.randomUUID().toString() else null
                snapshot.forEachIndexed { idx, draft ->
                    val persistedPath = when {
                        draft.sourceUri != null ->
                            fileStorage.copyUriToPageFile(draft.id, draft.sourceUri)?.absolutePath ?: draft.imagePath
                        else -> draft.imagePath
                    }
                    val ocr = draft.ocrText.ifBlank { "Scanned document: ${draft.title}" }
                    val lang = langEngine.detect(ocr)
                    val cls = classifyEngine.classify(OcrPage(text = ocr, title = draft.title))
                    val sum = summaryEngine.summarize(ocr, SummaryMode.EXTRACTIVE)
                    val entity = PageEntity(
                        id = draft.id,
                        batchId = batchId,
                        name = draft.title,
                        source = if (draft.sourceUri != null) "import" else "camera",
                        imagePath = persistedPath,
                        createdAt = System.currentTimeMillis() - idx * 1000L,
                        ocrText = ocr,
                        ocrLang = lang.lang,
                        ocrLangConfidence = lang.confidence,
                        docType = cls.docType,
                        docTypeConfidence = cls.confidence,
                        tags = cls.tags,
                        summary = sum.text,
                        summarySource = "extractive",
                        sortIndex = idx,
                    )
                    runtime.repo.insert(entity)
                    documents.add(0, entity)
                    selectedDocumentId = entity.id
                }
                val source = if (snapshot.firstOrNull()?.sourceUri != null) "import" else "camera"
                AnalyticsTracker.logEvent(
                    context,
                    "scan_save",
                    mapOf("n_pages" to snapshot.size, "source" to source, "is_pro" to isPro),
                )
            }
            draftPages.clear()
            editingDocumentId = null
            statusBanner = "Saved ${snapshot.size} page(s) locally"
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
        val idx = documents.indexOfFirst { it.id == doc.id }
        if (idx >= 0) documents[idx] = updated
    }

    fun removeTagFromSelected(tag: String) {
        val doc = selectedDocument() ?: return
        val updated = doc.copy(tags = doc.tags.filterNot { it == tag })
        val idx = documents.indexOfFirst { it.id == doc.id }
        if (idx >= 0) documents[idx] = updated
    }

    fun clearLocalLibrary() {
        documents.clear()
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

    private fun seedInitialDocuments() {
        val now = System.currentTimeMillis()
        documents += listOf(
            PageEntity(
                id = "doc-1",
                batchId = "batch-1",
                name = "Invoice Mar 12",
                source = "camera",
                imagePath = "",
                createdAt = now,
                ocrText = "INVOICE #1042-88\nBilled to: Acme Corp\nDate: March 12, 2025\nAmount: $1,240.00 USD\nTax ID: US-99214482\nPayment terms: Net-30 via ACH Direct Transfer",
                ocrLang = "en",
                ocrLangConfidence = 0.994f,
                docType = "Invoices",
                docTypeConfidence = 0.96f,
                tags = listOf("finance", "tax 2025", "vendor"),
                summary = "Vendor invoice for cloud infrastructure services ($1,240.00). Due March 31, 2025.",
                summarySource = "extractive",
            ),
            PageEntity(
                id = "doc-2",
                batchId = null,
                name = "Receipt_0912",
                source = "import",
                imagePath = "",
                createdAt = now - 86_400_000L,
                ocrText = "Blue Bottle Coffee\nTotal: $14.20\nTax 2024 · Paid via Card",
                ocrLang = "en",
                ocrLangConfidence = 0.98f,
                docType = "Receipts",
                docTypeConfidence = 0.94f,
                tags = listOf("tax 2024", "coffee"),
                summary = "Coffee receipt for $14.20 from Blue Bottle Coffee.",
                summarySource = "extractive",
            ),
            PageEntity(
                id = "doc-3",
                batchId = "batch-3",
                name = "Contract Lease Agreement",
                source = "camera",
                imagePath = "",
                createdAt = now - 3 * 86_400_000L,
                ocrText = "RESIDENTIAL LEASE AGREEMENT\nSigned between Landlord & Tenant for 12 months.",
                ocrLang = "en",
                ocrLangConfidence = 0.97f,
                docType = "Contracts",
                docTypeConfidence = 0.95f,
                tags = listOf("legal", "signed"),
                summary = "3-page signed lease agreement for 12-month term.",
                summarySource = "extractive",
            ),
            PageEntity(
                id = "doc-4",
                batchId = "batch-4",
                name = "Health Insurance Form",
                source = "camera",
                imagePath = "",
                createdAt = now - 7 * 86_400_000L,
                ocrText = "HEALTH INSURANCE POLICY #HI-4410\nMember ID: 8821-09\nCoverage: Comprehensive",
                ocrLang = "en",
                ocrLangConfidence = 0.96f,
                docType = "ID & Legal",
                docTypeConfidence = 0.92f,
                tags = listOf("medical"),
                summary = "2-page health insurance policy confirmation.",
                summarySource = "extractive",
            ),
        )
    }
}
