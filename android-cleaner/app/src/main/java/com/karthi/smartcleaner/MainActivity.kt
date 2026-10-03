package com.karthi.smartcleaner

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.karthi.smartcleaner.data.StoragePermission
import com.karthi.smartcleaner.ui.MainViewModel
import com.karthi.smartcleaner.ui.screens.DashboardScreen
import com.karthi.smartcleaner.ui.screens.DuplicatesScreen
import com.karthi.smartcleaner.ui.screens.FileFilter
import com.karthi.smartcleaner.ui.screens.FileListScreen
import com.karthi.smartcleaner.ui.screens.SettingsScreen
import com.karthi.smartcleaner.ui.theme.SmartCleanerTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent // Wait for DataStore to avoid a theme flash.
            SmartCleanerTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor) {
                CleanerNavHost(vm)
            }
        }
    }
}

@Composable
private fun CleanerNavHost(vm: MainViewModel) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var notificationsGranted by remember { mutableStateOf(StoragePermission.canPostNotifications(context)) }

    // Re-check after returning from the system "All files access" screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshPermission()
        notificationsGranted = StoragePermission.canPostNotifications(context)
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshPermission()
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshPermission()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) { if (!notificationsGranted) requestNotifications() }

    val requestStorage = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                allFilesLauncher.launch(StoragePermission.allFilesAccessIntent(context))
            } catch (_: ActivityNotFoundException) {
                allFilesLauncher.launch(StoragePermission.allFilesAccessFallbackIntent())
            }
        } else {
            legacyLauncher.launch(StoragePermission.legacyPermissions)
        }
    }

    NavHost(
        navController = nav,
        startDestination = "dashboard",
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable("dashboard") {
            DashboardScreen(
                vm = vm,
                snackbar = snackbar,
                onRequestStorage = requestStorage,
                onOpenFilter = { nav.navigate("files/${it.name}") },
                onOpenDuplicates = { nav.navigate("duplicates") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("files/{filter}", arguments = listOf(navArgument("filter") { type = NavType.StringType })) { entry ->
            val filter = entry.arguments?.getString("filter")
                ?.let { runCatching { FileFilter.valueOf(it) }.getOrNull() } ?: FileFilter.OTHER
            FileListScreen(vm, filter, snackbar, onBack = { nav.popBackStack() })
        }
        composable("duplicates") {
            DuplicatesScreen(vm, snackbar, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                vm = vm,
                notificationsGranted = notificationsGranted,
                onRequestNotifications = requestNotifications,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
