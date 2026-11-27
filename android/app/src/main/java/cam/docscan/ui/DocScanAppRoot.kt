package cam.docscan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import cam.docscan.DocScanApp
import cam.docscan.engine.DocScanRuntime
import cam.docscan.telemetry.AnalyticsTracker
import cam.docscan.ui.screens.BackupScreen
import cam.docscan.ui.screens.CameraCaptureScreen
import cam.docscan.ui.screens.CameraPermissionScreen
import cam.docscan.ui.screens.DocumentDetailScreen
import cam.docscan.ui.screens.ExportShareScreen
import cam.docscan.ui.screens.HomeLibraryScreen
import cam.docscan.ui.screens.LibrarySuggestScreen
import cam.docscan.ui.screens.OnboardingScreen
import cam.docscan.ui.screens.PaywallScreen
import cam.docscan.ui.screens.PhotoPermissionScreen
import cam.docscan.ui.screens.ReviewEditScreen
import cam.docscan.ui.screens.SearchFilterScreen
import cam.docscan.ui.screens.SettingsScreen
import cam.docscan.ui.state.DocScanAppState
import cam.docscan.ui.state.DocScanRoute
import cam.docscan.ui.state.MainTab

/**
 * Root navigation shell wiring all 13 Stitch screens to DocScanRuntime.
 */
@Composable
fun DocScanAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runtime = remember {
        (context.applicationContext as? DocScanApp)?.runtime ?: DocScanRuntime(context.applicationContext)
    }
    val state = remember { DocScanAppState(context.applicationContext, runtime, scope) }

    LaunchedEffect(Unit) {
        state.cameraPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        val mediaPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        state.photoPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            mediaPerm,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state.cameraPermissionGranted = granted
        state.currentRoute = if (granted) DocScanRoute.CAPTURE else DocScanRoute.HOME_LIBRARY
    }

    val photoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state.photoPermissionGranted = granted
        if (granted) {
            state.currentRoute = DocScanRoute.LIBRARY_SUGGEST
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            state.importUris(uris)
        }
    }

    val launchPhotoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    BackHandler(enabled = state.currentRoute != DocScanRoute.HOME_LIBRARY && state.currentRoute != DocScanRoute.ONBOARDING) {
        state.currentRoute = DocScanRoute.HOME_LIBRARY
        state.activeTab = MainTab.LIBRARY
    }

    LaunchedEffect(state.currentRoute) {
        AnalyticsTracker.logScreenView(context, state.currentRoute.name.lowercase())
        if (state.currentRoute == DocScanRoute.PAYWALL) {
            AnalyticsTracker.logEvent(context, "paywall_view", mapOf("is_pro" to state.isPro))
        }
    }

    when (state.currentRoute) {
        DocScanRoute.ONBOARDING -> OnboardingScreen(
            onGetStarted = {
                AnalyticsTracker.logEvent(context, "onboarding_complete")
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            },
        )

        DocScanRoute.CAMERA_PERMISSION -> CameraPermissionScreen(
            onAllowCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
            onNotNow = {
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            },
        )

        DocScanRoute.PHOTO_PERMISSION -> PhotoPermissionScreen(
            onAllowAccess = {
                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                photoPermissionLauncher.launch(perm)
            },
            onSelectManually = launchPhotoPicker,
            onNotNow = {
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            },
        )

        DocScanRoute.HOME_LIBRARY -> HomeLibraryScreen(
            state = state,
            onOpenImport = {
                if (state.photoPermissionGranted) {
                    launchPhotoPicker()
                } else {
                    state.currentRoute = DocScanRoute.PHOTO_PERMISSION
                }
            },
        )

        DocScanRoute.LIBRARY_SUGGEST -> LibrarySuggestScreen(
            state = state,
            onBack = {
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            },
        )

        DocScanRoute.CAPTURE -> CameraCaptureScreen(
            state = state,
            onOpenGallery = launchPhotoPicker,
            onBack = {
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            },
        )

        DocScanRoute.REVIEW_EDIT -> ReviewEditScreen(
            state = state,
            onAddMore = { state.currentRoute = DocScanRoute.CAPTURE },
            onDiscard = {
                state.draftPages.clear()
                state.editingDocumentId = null
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            },
        )

        DocScanRoute.DOC_DETAIL -> DocumentDetailScreen(
            state = state,
            onBack = {
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            },
        )

        DocScanRoute.SEARCH_FILTER -> SearchFilterScreen(
            state = state,
            onBack = {
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            },
        )

        DocScanRoute.EXPORT_SHARE -> ExportShareScreen(
            state = state,
            onBack = { state.currentRoute = DocScanRoute.DOC_DETAIL },
        )

        DocScanRoute.BACKUP -> BackupScreen(
            state = state,
            onBack = { state.currentRoute = DocScanRoute.HOME_LIBRARY },
        )

        DocScanRoute.PAYWALL -> PaywallScreen(
            state = state,
            onClose = { state.currentRoute = DocScanRoute.HOME_LIBRARY },
        )

        DocScanRoute.SETTINGS -> SettingsScreen(
            state = state,
            onBack = {
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            },
        )
    }
}
