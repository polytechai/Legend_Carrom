package com.example.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.model.OverlayConfig

/**
 * Movable Floating Control Window that floats over the game screen.
 * Provides quick toggles for guidance lines, cushion rebounds, and fast calibration.
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingControlView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val onToggleOverlay: (Boolean) -> Unit,
    private val onToggleCushions: (Boolean) -> Unit,
    private val onRecalibrate: () -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(context) {

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private var isEnabled = true
    private var isCushionEnabled = true
    private var isExpanded = false

    private val toggleBtn: TextView
    private val cushionBtn: TextView
    private val calibrateBtn: TextView
    private val closeBtn: TextView
    private val subControlsLayout: LinearLayout

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val density = context.resources.displayMetrics.density

        // Main floating pill container background
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(Color.parseColor("#E60F172A")) // 90% slate navy
            setStroke((1.5f * density).toInt(), Color.parseColor("#4D00E5FF"))
        }
        background = bgDrawable
        setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())

        // Header / Drag bar row
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Drag handle icon indicator
        val dragHandle = TextView(context).apply {
            text = "⠿ CARROM AIM"
            textSize = 12f
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding((6 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
        }
        headerRow.addView(dragHandle)

        // Toggle on/off button
        toggleBtn = TextView(context).apply {
            text = "● ON"
            textSize = 11f
            setTextColor(Color.WHITE)
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(Color.parseColor("#00E676"))
            }
            background = btnBg
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            setOnClickListener {
                isEnabled = !isEnabled
                text = if (isEnabled) "● ON" else "○ OFF"
                btnBg.setColor(if (isEnabled) Color.parseColor("#00E676") else Color.parseColor("#EF5350"))
                onToggleOverlay(isEnabled)
            }
        }
        headerRow.addView(toggleBtn)

        // Expand/Collapse quick menu button
        val expandBtn = TextView(context).apply {
            text = "⚙"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding((8 * density).toInt(), (2 * density).toInt(), (4 * density).toInt(), (2 * density).toInt())
            setOnClickListener {
                isExpanded = !isExpanded
                subControlsLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
            }
        }
        headerRow.addView(expandBtn)

        addView(headerRow)

        // Sub controls row (expandable quick controls)
        subControlsLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, (6 * density).toInt(), 0, 0)
        }

        cushionBtn = TextView(context).apply {
            text = "Cushions: ON"
            textSize = 10f
            setTextColor(Color.WHITE)
            val cBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                setColor(Color.parseColor("#334155"))
            }
            background = cBg
            setPadding((6 * density).toInt(), (3 * density).toInt(), (6 * density).toInt(), (3 * density).toInt())
            setOnClickListener {
                isCushionEnabled = !isCushionEnabled
                text = if (isCushionEnabled) "Cushions: ON" else "Cushions: OFF"
                onToggleCushions(isCushionEnabled)
            }
        }
        subControlsLayout.addView(cushionBtn)

        val spacer1 = View(context).apply {
            layoutParams = LayoutParams((6 * density).toInt(), 1)
        }
        subControlsLayout.addView(spacer1)

        calibrateBtn = TextView(context).apply {
            text = "Calibrate"
            textSize = 10f
            setTextColor(Color.parseColor("#FFD600"))
            val calBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                setColor(Color.parseColor("#334155"))
            }
            background = calBg
            setPadding((6 * density).toInt(), (3 * density).toInt(), (6 * density).toInt(), (3 * density).toInt())
            setOnClickListener {
                onRecalibrate()
            }
        }
        subControlsLayout.addView(calibrateBtn)

        val spacer2 = View(context).apply {
            layoutParams = LayoutParams((6 * density).toInt(), 1)
        }
        subControlsLayout.addView(spacer2)

        closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 11f
            setTextColor(Color.parseColor("#FF5252"))
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            setOnClickListener {
                onClose()
            }
        }
        subControlsLayout.addView(closeBtn)

        addView(subControlsLayout)

        // Drag listener on the entire floating pill
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > 10) {
                        isDragging = true
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        windowManager.updateViewLayout(this, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging
                }
                else -> false
            }
        }
    }
}
