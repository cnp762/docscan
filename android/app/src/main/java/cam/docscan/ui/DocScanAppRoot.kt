package cam.docscan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import cam.docscan.ui.components.rememberReducedMotion
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
        when (state.currentRoute) {
            DocScanRoute.EXPORT_SHARE -> state.currentRoute = DocScanRoute.DOC_DETAIL
            DocScanRoute.REVIEW_EDIT -> {
                if (state.editingDocumentId != null) {
                    state.draftPages.clear()
                    state.editingDocumentId = null
                    state.currentRoute = DocScanRoute.DOC_DETAIL
                } else {
                    state.draftPages.clear()
                    state.currentRoute = DocScanRoute.HOME_LIBRARY
                    state.activeTab = MainTab.LIBRARY
                }
            }
            else -> {
                state.currentRoute = DocScanRoute.HOME_LIBRARY
                state.activeTab = MainTab.LIBRARY
            }
        }
    }

    LaunchedEffect(state.currentRoute) {
        AnalyticsTracker.logScreenView(context, state.currentRoute.name.lowercase())
        if (state.currentRoute == DocScanRoute.PAYWALL) {
            AnalyticsTracker.logEvent(context, "paywall_view", mapOf("is_pro" to state.isPro))
        }
    }

    val reducedMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = state.currentRoute,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(120)) togetherWith fadeOut(tween(100))
            } else {
                val isModal = targetState == DocScanRoute.PAYWALL || targetState == DocScanRoute.CAPTURE
                val isClosingModal = initialState == DocScanRoute.PAYWALL || initialState == DocScanRoute.CAPTURE
                when {
                    isModal -> (slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 8 } + fadeIn(tween(200))) togetherWith
                        fadeOut(tween(150))
                    isClosingModal -> fadeIn(tween(180)) togetherWith
                        (slideOutVertically(tween(180)) { it / 8 } + fadeOut(tween(160)))
                    targetState == DocScanRoute.HOME_LIBRARY -> (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 10 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(160)) { it / 10 } + fadeOut(tween(150)))
                    else -> (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(160)) { -it / 10 } + fadeOut(tween(150)))
                }
            }
        },
        label = "routeTransition",
    ) { route ->
        when (route) {
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
}
