package com.example.service

import android.annotation.SuppressLint
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
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
 * Foreground Service that manages:
 * - MediaProjection real-time screen capture
 * - Fullscreen pass-through TrajectoryOverlayView (hidden by default)
 * - Interactive Striker Touch Zone with dynamic visibility and power mapping
 * - Floating movable Control Window
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

    // Dynamic Touch & Aim State
    private var isDraggingStriker = false
    private var dragStart = Vector2D(540f, 1800f)
    private var currentPowerPercent = 50f
    private var strikerTouchPad: View? = null

    // Simulated / fallback aim state for interactive live guidance
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

        createTrajectoryOverlay()
        createStrikerTouchZone()
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

        val action = intent.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

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

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

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

    private fun startSimulationLoop() {
        mainHandler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                if (isDraggingStriker && overlayConfig.isOverlayEnabled) {
                    updateTrajectory()
                }

                mainHandler.postDelayed(this, 16) // ~60 FPS
            }
        })
    }

    private fun createTrajectoryOverlay() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

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

        // Hidden by default: only rendered when user drags inside striker zone
        overlayView = TrajectoryOverlayView(this).apply {
            config = overlayConfig
            visibility = View.GONE
        }
        windowManager.addView(overlayView, params)
    }

    /**
     * Interactive transparent touch window positioned over the bottom striker baseline.
     * Detects user touch-down, drag trajectory, and release (touch-up) events.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createStrikerTouchZone() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val touchPadHeight = (180 * density).toInt()
        val touchPadWidth = (screenWidth * 0.90f).toInt()

        val params = WindowManager.LayoutParams(
            touchPadWidth,
            touchPadHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (screenHeight * 0.10f).toInt()
        }

        strikerTouchPad = View(this).apply {
            setOnTouchListener { _, event ->
                handleStrikerTouch(event)
            }
        }
        windowManager.addView(strikerTouchPad, params)
    }

    /**
     * Maps user drag motion on striker area to dynamic power and angle.
     */
    private fun handleStrikerTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDraggingStriker = true
                dragStart = Vector2D(event.rawX, event.rawY)
                currentPowerPercent = 15f

                // Dynamic Trigger: Reveal guidelines when user touches down
                overlayView?.visibility = View.VISIBLE
                updateTrajectory()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingStriker) {
                    val currentPos = Vector2D(event.rawX, event.rawY)
                    val dragVector = currentPos - dragStart
                    val dragDist = dragVector.length()
                    val maxDragDist = 240f * resources.displayMetrics.density

                    // Power 0% to 100% mapped to drag distance
                    currentPowerPercent = ((dragDist / maxDragDist) * 100f).coerceIn(10f, 100f)

                    // Slingshot mechanic: dragging backwards pulls the striker for forward launch
                    if (dragDist > 10f) {
                        val aimDir = Vector2D(-dragVector.x, -dragVector.y).normalized()
                        aimAngleDegrees = aimDir.angleDegrees()
                    }
                    updateTrajectory()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Dynamic Trigger: Immediately hide guidelines when striker is released
                isDraggingStriker = false
                overlayView?.visibility = View.GONE
                return true
            }
        }
        return false
    }

    private fun updateTrajectory() {
        if (!isDraggingStriker || !overlayConfig.isOverlayEnabled) return

        val aimDir = Vector2D.fromAngle(aimAngleDegrees)
        val trajectory = physicsCalculator.calculateTrajectory(
            strikerPos = strikerPosition,
            aimDirection = aimDir,
            pucks = pucks,
            maxBounces = if (overlayConfig.showCushionBounces) overlayConfig.maxCushionBounces else 0,
            allowSecondaryCollision = overlayConfig.showSecondaryCollisions,
            powerPercent = currentPowerPercent
        )
        overlayView?.trajectoryResult = trajectory
    }

    private fun createFloatingControls() {
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
        windowManager.addView(floatingControlView, params)
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

        overlayView?.let { windowManager.removeView(it) }
        floatingControlView?.let { windowManager.removeView(it) }
        strikerTouchPad?.let { windowManager.removeView(it) }
        overlayView = null
        floatingControlView = null
        strikerTouchPad = null
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
