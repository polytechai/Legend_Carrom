package com.example.model

/**
 * Carrom board boundary, cushion segments, baselines and corner pockets.
 * Normalized to a coordinate system [left, top, right, bottom].
 */
data class BoardGeometry(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1000f,
    val bottom: Float = 1000f,
    val cushionInset: Float = 50f,
    val pocketRadius: Float = 48f,
    val strikerRadius: Float = 34f,
    val puckRadius: Float = 24f
) {
    val playLeft: Float get() = left + cushionInset
    val playTop: Float get() = top + cushionInset
    val playRight: Float get() = right - cushionInset
    val playBottom: Float get() = bottom - cushionInset

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    // 4 Corner Pockets centered slightly inside corner cushions
    val pockets: List<Pocket>
        get() = listOf(
            Pocket(0, Vector2D(playLeft + 12f, playTop + 12f), pocketRadius, "Top-Left"),
            Pocket(1, Vector2D(playRight - 12f, playTop + 12f), pocketRadius, "Top-Right"),
            Pocket(2, Vector2D(playLeft + 12f, playBottom - 12f), pocketRadius, "Bottom-Left"),
            Pocket(3, Vector2D(playRight - 12f, playBottom - 12f), pocketRadius, "Bottom-Right")
        )

    // Player bottom baseline (where striker is positioned)
    val bottomBaselineY: Float get() = playBottom - 160f
    val baselineLeftX: Float get() = playLeft + 120f
    val baselineRightX: Float get() = playRight - 120f
}

data class Pocket(
    val id: Int,
    val position: Vector2D,
    val radius: Float,
    val name: String
)
