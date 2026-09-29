package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.R
import kotlin.math.hypot

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var isAimLinesActive = true
    private var isAssistModeActive = false
    private var isExpanded = false

    companion object {
        const val CHANNEL_ID = "carrom_bubble_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_TOGGLE_AIM = "com.example.ACTION_TOGGLE_AIM"
        const val ACTION_TOGGLE_ASSIST = "com.example.ACTION_TOGGLE_ASSIST"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        createFloatingBubble()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        val density = resources.displayMetrics.density
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (20 * density).toInt()
            y = (180 * density).toInt()
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Expanded Control Menu
        val menuContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor(Color.parseColor("#EE0F172A"))
                setStroke((1.5f * density).toInt(), Color.parseColor("#00E5FF"))
            }
            background = bg
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
        }

        val aimLineBtn = createMenuButton("Aim Lines: ON", Color.parseColor("#00E5FF")) { btn ->
            isAimLinesActive = !isAimLinesActive
            btn.text = if (isAimLinesActive) "Aim Lines: ON" else "Aim Lines: OFF"
            btn.setTextColor(if (isAimLinesActive) Color.parseColor("#00E5FF") else Color.parseColor("#94A3B8"))
            sendBroadcast(Intent(ACTION_TOGGLE_AIM).putExtra("enabled", isAimLinesActive))
        }
        menuContainer.addView(aimLineBtn)

        val assistBtn = createMenuButton("Assist: OFF", Color.parseColor("#94A3B8")) { btn ->
            isAssistModeActive = !isAssistModeActive
            btn.text = if (isAssistModeActive) "Assist: ON" else "Assist: OFF"
            btn.setTextColor(if (isAssistModeActive) Color.parseColor("#00E676") else Color.parseColor("#94A3B8"))
            sendBroadcast(Intent(ACTION_TOGGLE_ASSIST).putExtra("enabled", isAssistModeActive))
        }
        menuContainer.addView(assistBtn)

        val closeBtn = createMenuButton("Close Bubble", Color.parseColor("#EF5350")) {
            stopSelf()
        }
        menuContainer.addView(closeBtn)

        rootLayout.addView(menuContainer)

        // Floating Logo Bubble
        val bubbleSize = (56 * density).toInt()
        val logoBubble = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                topMargin = (6 * density).toInt()
            }
            setImageResource(R.mipmap.ic_launcher)
            val bubbleBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0F172A"))
                setStroke((2 * density).toInt(), Color.parseColor("#00E5FF"))
            }
            background = bubbleBg
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }

        // Dragging & Tap Gesture Handler
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDrag = false

        logoBubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (hypot(dx.toDouble(), dy.toDouble()) > 10) {
                        isDrag = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(rootLayout, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrag) {
                        // Toggle menu visibility
                        isExpanded = !isExpanded
                        menuContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    } else {
                        // Edge-snapping: snap to nearest screen edge (left or right)
                        val screenWidth = resources.displayMetrics.widthPixels
                        val targetX = if (params.x + bubbleSize / 2 < screenWidth / 2) (12 * density).toInt() else screenWidth - bubbleSize - (12 * density).toInt()
                        params.x = targetX
                        windowManager.updateViewLayout(rootLayout, params)
                    }
                    true
                }
                else -> false
            }
        }

        rootLayout.addView(logoBubble)
        windowManager.addView(rootLayout, params)
        bubbleView = rootLayout
    }

    private fun createMenuButton(label: String, textColor: Int, onClick: (TextView) -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(textColor)
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            setOnClickListener { onClick(this) }
        }
    }

    private fun buildForegroundNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Bubble Aim Guide",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Legend Carrom Bubble")
            .setContentText("Overlay active over game screen")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
