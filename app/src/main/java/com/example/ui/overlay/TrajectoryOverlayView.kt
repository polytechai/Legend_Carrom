package com.example.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.model.OverlayConfig
import com.example.model.TrajectoryNode
import com.example.model.TrajectoryNodeType
import com.example.model.TrajectoryResult
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom View for rendering real-time Carrom trajectory lines, contact ghost circles,
 * cushion rebound paths, multi-ball combo lines, and angle/power indicators on Canvas.
 */
class TrajectoryOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var config: OverlayConfig = OverlayConfig()
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var trajectoryResult: TrajectoryResult = TrajectoryResult()
        set(value) {
            field = value
            invalidate()
        }

    // Paints
    private val strikerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val puckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val cushionBouncePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val secondaryPuckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val ghostCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val ghostCircleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(45, 0, 229, 255)
    }

    private val nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val hudCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(210, 15, 23, 42) // Dark Slate navy
    }

    private val hudCardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(120, 0, 229, 255)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 203, 213, 225)
        textSize = 24f
    }

    private val powerBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(100, 51, 65, 85)
    }

    private val powerBarFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00E676")
    }

    init {
        updatePaints()
    }

    private fun updatePaints() {
        val density = resources.displayMetrics.density
        val strokePx = config.strokeWidthDp * density

        strikerPaint.strokeWidth = strokePx
        strikerPaint.color = config.strikerLineColor

        puckPaint.strokeWidth = strokePx
        puckPaint.color = config.puckLineColor

        cushionBouncePaint.strokeWidth = strokePx * 0.9f
        cushionBouncePaint.color = config.cushionLineColor
        cushionBouncePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)

        secondaryPuckPaint.strokeWidth = strokePx * 0.85f
        secondaryPuckPaint.color = config.secondaryLineColor

        ghostCirclePaint.color = config.ghostCircleColor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!config.isOverlayEnabled) return

        // 1. Draw Striker Trajectory Segments
        for (segment in trajectoryResult.strikerPath) {
            val paint = if (segment.isCushionBounce) cushionBouncePaint else strikerPaint
            canvas.drawLine(segment.start.x, segment.start.y, segment.end.x, segment.end.y, paint)
        }

        // 2. Draw Target Puck Trajectory Segments
        for (segment in trajectoryResult.targetPuckPath) {
            val paint = if (segment.isCushionBounce && config.showCushionBounces) cushionBouncePaint else puckPaint
            canvas.drawLine(segment.start.x, segment.start.y, segment.end.x, segment.end.y, paint)
        }

        // 3. Draw Secondary Combo Branch Segments
        if (config.showSecondaryCollisions) {
            for (segment in trajectoryResult.secondaryPuckPath) {
                canvas.drawLine(segment.start.x, segment.start.y, segment.end.x, segment.end.y, secondaryPuckPaint)
            }
        }

        // 4. Draw Ghost Contact Circle at Impact Point
        if (config.showGhostPuck) {
            trajectoryResult.contactGhostPuckPos?.let { ghostPos ->
                val r = 34f // Striker radius
                canvas.drawCircle(ghostPos.x, ghostPos.y, r, ghostCircleFillPaint)
                canvas.drawCircle(ghostPos.x, ghostPos.y, r, ghostCirclePaint)
                // Small center crosshair
                canvas.drawLine(ghostPos.x - 8f, ghostPos.y, ghostPos.x + 8f, ghostPos.y, ghostCirclePaint)
                canvas.drawLine(ghostPos.x, ghostPos.y - 8f, ghostPos.x, ghostPos.y + 8f, ghostCirclePaint)
            }
        }

        // 5. Draw Circular Node Markers
        for (node in trajectoryResult.nodes) {
            when (node.type) {
                TrajectoryNodeType.STRIKER_START -> {
                    nodeFillPaint.color = config.strikerLineColor
                    canvas.drawCircle(node.position.x, node.position.y, 8f, nodeFillPaint)
                }
                TrajectoryNodeType.PUCK_CONTACT -> {
                    nodeFillPaint.color = config.puckLineColor
                    canvas.drawCircle(node.position.x, node.position.y, 10f, nodeFillPaint)
                    canvas.drawCircle(node.position.x, node.position.y, 14f, nodeStrokePaint)
                }
                TrajectoryNodeType.CUSHION_BOUNCE -> {
                    if (config.showCushionBounces) {
                        nodeFillPaint.color = config.cushionLineColor
                        canvas.drawCircle(node.position.x, node.position.y, 11f, nodeFillPaint)
                        canvas.drawCircle(node.position.x, node.position.y, 16f, nodeStrokePaint)
                    }
                }
                TrajectoryNodeType.SECONDARY_CONTACT -> {
                    if (config.showSecondaryCollisions) {
                        nodeFillPaint.color = config.secondaryLineColor
                        canvas.drawCircle(node.position.x, node.position.y, 10f, nodeFillPaint)
                        canvas.drawCircle(node.position.x, node.position.y, 14f, nodeStrokePaint)
                    }
                }
                TrajectoryNodeType.POCKET_DESTINATION -> {
                    // Concentric pocket targeting rings
                    nodeFillPaint.color = Color.parseColor("#00E676")
                    canvas.drawCircle(node.position.x, node.position.y, 22f, nodeFillPaint)
                    canvas.drawCircle(node.position.x, node.position.y, 36f, nodeStrokePaint)
                }
            }
        }

        // 6. Draw HUD: Angle & Power Meter Card
        if (config.showAnglePowerMeter && trajectoryResult.strikerPath.isNotEmpty()) {
            drawHudCard(canvas)
        }
    }

    private fun drawHudCard(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val cardWidth = 260f * density
        val cardHeight = 84f * density
        val cardLeft = (width - cardWidth) / 2f
        val cardTop = 36f * density
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

        // Card background
        canvas.drawRoundRect(cardRect, 20f * density, 20f * density, hudCardPaint)
        canvas.drawRoundRect(cardRect, 20f * density, 20f * density, hudCardStrokePaint)

        // Angle & Pocket info text
        val angleStr = "Angle: ${"%.1f".format(trajectoryResult.shotAngleDegrees)}°"
        val cutStr = if (trajectoryResult.cutAngleDegrees > 0) " (Cut ${"%.0f".format(trajectoryResult.cutAngleDegrees)}°)" else ""
        canvas.drawText("$angleStr$cutStr", cardLeft + 16f * density, cardTop + 28f * density, textPaint)

        val pocketText = if (trajectoryResult.alignedPocket != null) {
            "Aim: ${trajectoryResult.alignedPocket?.name} (${trajectoryResult.pocketConfidence.toInt()}%)"
        } else {
            "Rec Power: ${trajectoryResult.recommendedPower}%"
        }
        canvas.drawText(pocketText, cardLeft + 16f * density, cardTop + 50f * density, subTextPaint)

        // Power gauge bar at the bottom of the card
        val barLeft = cardLeft + 16f * density
        val barTop = cardTop + 62f * density
        val barWidth = cardWidth - 32f * density
        val barHeight = 8f * density

        val barBgRect = RectF(barLeft, barTop, barLeft + barWidth, barTop + barHeight)
        canvas.drawRoundRect(barBgRect, 4f * density, 4f * density, powerBarBgPaint)

        val fillWidth = (barWidth * (trajectoryResult.recommendedPower / 100f)).coerceIn(0f, barWidth)
        val barFillRect = RectF(barLeft, barTop, barLeft + fillWidth, barTop + barHeight)

        // Dynamic power color: Green for optimal, Yellow for medium, Red for hard
        val power = trajectoryResult.recommendedPower
        powerBarFillPaint.color = when {
            power > 85 -> Color.parseColor("#FF5252")
            power > 60 -> Color.parseColor("#FFD600")
            else -> Color.parseColor("#00E676")
        }
        canvas.drawRoundRect(barFillRect, 4f * density, 4f * density, powerBarFillPaint)
    }
}
