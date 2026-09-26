package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.OverlayConfig
import com.example.model.Puck
import com.example.model.TrickShot
import com.example.model.Vector2D
import com.example.service.AppUpdater
import com.example.service.OverlayService
import com.example.ui.home.OverlayHomeScreen
import com.example.ui.practice.PracticeSimulatorScreen
import com.example.ui.settings.OverlaySettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.trickshots.TrickShotLibraryScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(
                    onStartService = { resultCode, resultData ->
                        startOverlayService(resultCode, resultData)
                    },
                    onStopService = {
                        stopOverlayService()
                    },
                    onRequestOverlayPermission = {
                        requestOverlayPermission()
                    },
                    checkOverlayPermission = {
                        Settings.canDrawOverlays(this)
                    }
                )
            }
        }
    }

    private fun startOverlayService(resultCode: Int, data: Intent?) {
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Carrom Aim overlay started!", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Overlay service stopped", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}

@Composable
fun MainAppContent(
    onStartService: (Int, Intent?) -> Unit,
    onStopService: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    checkOverlayPermission: () -> Boolean
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isOverlayPermissionGranted by remember { mutableStateOf(checkOverlayPermission()) }
    var isServiceRunning by remember { mutableStateOf(false) }

    // Config state
    var overlayConfig by remember { mutableStateOf(OverlayConfig()) }

    // Preloaded trick shot state for Simulator
    var simulatorInitialPucks by remember { mutableStateOf<List<Puck>?>(null) }
    var simulatorInitialStrikerPos by remember { mutableStateOf<Vector2D?>(null) }
    var simulatorTargetAngle by remember { mutableStateOf<Float?>(null) }
    var simulatorTargetPower by remember { mutableStateOf<Int?>(null) }

    // Auto-Updater state
    var updateInfo by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var updateErrorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Automatically check for GitHub release updates on startup
    LaunchedEffect(Unit) {
        val info = AppUpdater.checkForUpdates()
        if (info != null) {
            updateInfo = info
        }
    }

    // Recheck permission when returning to the app
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isOverlayPermissionGranted = checkOverlayPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // MediaProjection screen capture launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            isServiceRunning = true
            onStartService(result.resultCode, result.data)
        } else {
            isServiceRunning = true
            onStartService(0, null)
        }
    }

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Layers, contentDescription = "Overlay") },
                    label = { Text("Overlay") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F172A),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("nav_tab_overlay")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Sports, contentDescription = "Simulator") },
                    label = { Text("Simulator") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F172A),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("nav_tab_simulator")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.School, contentDescription = "Trick Shots") },
                    label = { Text("Trick Shots") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F172A),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("nav_tab_trick_shots")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F172A),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A))
        ) {
            when (selectedTab) {
                0 -> OverlayHomeScreen(
                    isOverlayPermissionGranted = isOverlayPermissionGranted,
                    isServiceRunning = isServiceRunning,
                    onStartOverlayClick = {
                        if (!isOverlayPermissionGranted) {
                            onRequestOverlayPermission()
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                        }
                    },
                    onStopOverlayClick = {
                        isServiceRunning = false
                        onStopService()
                    },
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onNavigateToSimulator = { selectedTab = 1 },
                    onNavigateToTrickShots = { selectedTab = 2 }
                )

                1 -> PracticeSimulatorScreen(
                    initialPucks = simulatorInitialPucks,
                    initialStrikerPos = simulatorInitialStrikerPos,
                    targetAngle = simulatorTargetAngle,
                    targetPower = simulatorTargetPower,
                    onNavigateToTrickShots = { selectedTab = 2 }
                )

                2 -> TrickShotLibraryScreen(
                    onLoadTrickShotInSimulator = { trickShot ->
                        simulatorInitialPucks = trickShot.pucks
                        simulatorInitialStrikerPos = trickShot.initialStrikerPos
                        simulatorTargetAngle = trickShot.recommendedAngle
                        simulatorTargetPower = trickShot.recommendedPower
                        selectedTab = 1
                    }
                )

                3 -> OverlaySettingsScreen(
                    config = overlayConfig,
                    onConfigChange = { overlayConfig = it }
                )
            }

            // In-App Auto Update Dialog
            if (updateInfo != null) {
                val update = updateInfo!!
                AlertDialog(
                    onDismissRequest = {
                        if (!isDownloadingUpdate) updateInfo = null
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "New Update Available!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "A new version of Legend Carrom is available.\nVersion: v${update.latestVersion} (Current: v${update.currentVersion})",
                                color = Color(0xFFCBD5E1),
                                fontSize = 14.sp
                            )
                            if (update.releaseNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Release Notes:\n${update.releaseNotes}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    maxLines = 4
                                )
                            }
                            if (isDownloadingUpdate) {
                                Spacer(modifier = Modifier.height(14.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color(0xFF334155)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Downloading APK: $downloadProgress%",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp
                                )
                            }
                            if (updateErrorMessage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = updateErrorMessage!!,
                                    color = Color(0xFFEF5350),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (!isDownloadingUpdate) {
                                    isDownloadingUpdate = true
                                    updateErrorMessage = null
                                    coroutineScope.launch {
                                        AppUpdater.downloadAndInstall(
                                            context = context,
                                            downloadUrl = update.downloadUrl,
                                            fileName = update.apkFileName,
                                            onProgress = { progress ->
                                                downloadProgress = progress
                                            },
                                            onError = { error ->
                                                isDownloadingUpdate = false
                                                updateErrorMessage = error
                                            }
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            enabled = !isDownloadingUpdate
                        ) {
                            Text(
                                if (isDownloadingUpdate) "Downloading..." else "Update Now",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        if (!isDownloadingUpdate) {
                            OutlinedButton(onClick = { updateInfo = null }) {
                                Text("Later", color = Color(0xFF94A3B8))
                            }
                        }
                    },
                    containerColor = Color(0xFF1E293B)
                )
            }
        }
    }
}
