package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.BoardGeometry
import com.example.model.OverlayConfig
import com.example.model.Puck
import com.example.model.PuckType
import com.example.model.Vector2D
import com.example.physics.PhysicsCalculator
import com.example.ui.overlay.FloatingControlView
import com.example.ui.overlay.TrajectoryOverlayView
import com.example.vision.BoardDetector

/**
 * Foreground Service responsible for:
 * 1. Attaching a transparent, full-screen pass-through Canvas layer to WindowManager.
 * 2. Continuous 30-60 FPS trajectory line calculations & rendering directly over other apps.
 * 3. MediaProjection screen frame capture & computer vision element tracking.
 * 4. Providing a movable floating toggle pill to control guidelines in-game.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: TrajectoryOverlayView? = null
    private var floatingControlView: FloatingControlView? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val physicsCalculator = PhysicsCalculator()
    private val boardDetector = BoardDetector()
    private var boardGeometry = BoardGeometry()
    private var overlayConfig = OverlayConfig()

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 2

    private var isProcessingFrame = false
    private var isRunning = false

    // Real-time aiming state
    private var aimAngleDegrees = 65f
    private var strikerPosition = Vector2D(540f, 1800f)
    private var pucks = mutableListOf<Puck>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        boardGeometry = BoardGeometry(
            left = 40f,
            top = (screenHeight - screenWidth) / 2f,
            right = screenWidth - 40f,
            bottom = (screenHeight + screenWidth) / 2f
        )

        initDefaultPucks()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // 1. Attach transparent Canvas overlay to WindowManager
        createTrajectoryOverlay()

        // 2. Attach interactive draggable floating control button
        createFloatingControls()

        isRunning = true
        startSimulationLoop()
    }

    private fun initDefaultPucks() {
        val cx = boardGeometry.centerX
        val cy = boardGeometry.centerY
        strikerPosition = Vector2D(cx, boardGeometry.bottomBaselineY)

        pucks.clear()
        pucks.add(Puck(1, Vector2D(cx, cy), type = PuckType.QUEEN))
        pucks.add(Puck(2, Vector2D(cx - 60f, cy - 40f), type = PuckType.WHITE))
        pucks.add(Puck(3, Vector2D(cx + 60f, cy - 40f), type = PuckType.BLACK))
        pucks.add(Puck(4, Vector2D(cx, cy - 90f), type = PuckType.WHITE))
        pucks.add(Puck(5, Vector2D(cx + 120f, cy + 80f), type = PuckType.WHITE))
        pucks.add(Puck(6, Vector2D(cx - 120f, cy + 80f), type = PuckType.BLACK))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        if (intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // MediaProjection token passed from Activity
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != 0 && resultData != null) {
            setupMediaProjection(resultCode, resultData)
        }

        return START_STICKY
    }

    /**
     * Attaches the transparent full-screen Canvas overlay layer to the WindowManager.
     * Uses pass-through flags so touches directly penetrate to the game running underneath.
     */
    private fun createTrajectoryOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // WindowManager parameters for non-intrusive transparent rendering:
        // - FLAG_NOT_TOUCHABLE: Touch events pass through to the game beneath
        // - FLAG_NOT_FOCUSABLE: Key and input focus remain on the active game
        // - FLAG_LAYOUT_NO_LIMITS: Extends rendering across status bar and notch area
        // - FLAG_HARDWARE_ACCELERATED: Hardware-accelerated GPU pipeline for 60 FPS
        // - PixelFormat.TRANSLUCENT: Fully transparent Canvas background
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        overlayView = TrajectoryOverlayView(this).apply {
            config = overlayConfig
        }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            // Handled safely in case of revoked permissions
        }
    }

    /**
     * Attaches the movable floating pill widget to WindowManager.
     * This view accepts touch gestures so users can reposition it and toggle features.
     */
    private fun createFloatingControls() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }

        floatingControlView = FloatingControlView(
            context = this,
            windowManager = windowManager,
            layoutParams = params,
            onToggleOverlay = { enabled ->
                overlayConfig = overlayConfig.copy(isOverlayEnabled = enabled)
                overlayView?.config = overlayConfig
            },
            onToggleCushions = { enabled ->
                overlayConfig = overlayConfig.copy(showCushionBounces = enabled)
                overlayView?.config = overlayConfig
            },
            onRecalibrate = {
                aimAngleDegrees = (aimAngleDegrees + 15f) % 360f
            },
            onClose = {
                stopSelf()
            }
        )

        try {
            windowManager.addView(floatingControlView, params)
        } catch (e: Exception) {
            // Handled safely
        }
    }

    /**
     * Initializes MediaProjection virtual display for screen capture.
     */
    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        // Capture at 1/2 resolution to balance CV accuracy with 60 FPS performance
        val captureWidth = screenWidth / 2
        val captureHeight = screenHeight / 2

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            if (!isProcessingFrame && overlayConfig.isOverlayEnabled) {
                processScreenFrame(reader)
            } else {
                reader.acquireLatestImage()?.close()
            }
        }, null)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "CarromAimCapture",
            captureWidth,
            captureHeight,
            screenDensity / 2,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    /**
     * Analyzes captured screen frames and extracts board geometry, pucks, and striker.
     */
    private fun processScreenFrame(reader: ImageReader) {
        isProcessingFrame = true
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                val result = boardDetector.processFrame(bitmap, boardGeometry)

                if (result.pucks.isNotEmpty()) {
                    pucks.clear()
                    pucks.addAll(result.pucks)
                }
                result.striker?.let { detectedStriker ->
                    strikerPosition = detectedStriker.position
                }

                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Frame capture exception handled gracefully
        } finally {
            image?.close()
            isProcessingFrame = false
        }
    }

    /**
     * 60 FPS update loop that recalculates 2D trajectory vectors and triggers Canvas invalidation.
     */
    private fun startSimulationLoop() {
        mainHandler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                if (overlayConfig.isOverlayEnabled) {
                    val aimDir = Vector2D.fromAngle(aimAngleDegrees)
                    val trajectory = physicsCalculator.calculateTrajectory(
                        strikerPos = strikerPosition,
                        aimDirection = aimDir,
                        pucks = pucks,
                        maxBounces = if (overlayConfig.showCushionBounces) overlayConfig.maxCushionBounces else 0,
                        allowSecondaryCollision = overlayConfig.showSecondaryCollisions
                    )

                    overlayView?.trajectoryResult = trajectory
                }

                mainHandler.postDelayed(this, 16) // ~60 FPS
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Carrom Aim Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active Carrom trajectory prediction overlay"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Carrom Aim Guide Active")
            .setContentText("Trajectory lines and physics overlay running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Overlay", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)

        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()

        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View already detached
            }
        }
        floatingControlView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View already detached
            }
        }
        overlayView = null
        floatingControlView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "carrom_overlay_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "com.example.carromaim.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
