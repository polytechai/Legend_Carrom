package com.example.model

data class TrickShot(
    val id: String,
    val title: String,
    val category: String,
    val difficulty: String, // Beginner, Intermediate, Master
    val description: String,
    val cueTip: String,
    val initialStrikerPos: Vector2D,
    val pucks: List<Puck>,
    val targetPocketIndex: Int,
    val recommendedAngle: Float,
    val recommendedPower: Int,
    val keyConcepts: List<String>
)

object TrickShotCatalog {
    fun getClassicShots(board: BoardGeometry): List<TrickShot> {
        val cx = board.centerX
        val cy = board.centerY
        val bY = board.bottomBaselineY
        val pLeft = board.playLeft
        val pRight = board.playRight
        val pTop = board.playTop

        return listOf(
            TrickShot(
                id = "bank_top_right",
                title = "Single Cushion Bank Shot",
                category = "Cushion & Rebound",
                difficulty = "Intermediate",
                description = "Rebound the striker or puck off the side cushion at an equal angle of incidence and reflection to pot into the opposite corner pocket.",
                cueTip = "Aim at the mirror point on the right cushion. Strike with 68% power so the rebound maintains enough momentum to reach the pocket mouth.",
                initialStrikerPos = Vector2D(cx - 140f, bY),
                pucks = listOf(
                    Puck(1, Vector2D(cx + 80f, cy - 60f), type = PuckType.WHITE)
                ),
                targetPocketIndex = 1, // Top-Right
                recommendedAngle = 48.5f,
                recommendedPower = 68,
                keyConcepts = listOf("Equal Angle Reflection", "Side Cushion Damping", "Target Pocket Mirroring")
            ),
            TrickShot(
                id = "cross_shot_classic",
                title = "Cross Cut Shot",
                category = "Cut & Carrom",
                difficulty = "Intermediate",
                description = "Position the striker on the baseline far to one side and execute a sharp cross-cut on a puck near the center circle towards the far pocket.",
                cueTip = "A thin cut (35° cut angle). Ensure striker speed isn't too soft; a crisp release imparts pure forward impulse to the puck.",
                initialStrikerPos = Vector2D(board.baselineLeftX + 30f, bY),
                pucks = listOf(
                    Puck(2, Vector2D(cx - 30f, cy - 30f), type = PuckType.BLACK),
                    Puck(3, Vector2D(cx + 40f, cy + 40f), type = PuckType.WHITE)
                ),
                targetPocketIndex = 1, // Top-Right
                recommendedAngle = 42.0f,
                recommendedPower = 72,
                keyConcepts = listOf("Center-to-Center Line", "Impact Tangent", "Deflection Angle")
            ),
            TrickShot(
                id = "back_shot_rebound",
                title = "Baseline Back Shot",
                category = "Back-Shot",
                difficulty = "Master",
                description = "When a puck is resting dangerously close behind your striker baseline, hit backwards into the bottom cushion to bounce forward and pot the puck.",
                cueTip = "Aim downward into the bottom cushion at 84° to 96°. The bottom cushion rebound fires straight upward directly into the puck.",
                initialStrikerPos = Vector2D(cx, bY),
                pucks = listOf(
                    Puck(4, Vector2D(cx, bY + 50f), type = PuckType.QUEEN)
                ),
                targetPocketIndex = 0, // Top-Left or Top-Right
                recommendedAngle = 92.0f,
                recommendedPower = 85,
                keyConcepts = listOf("Perpendicular Cushion Strike", "Direct Back Reflection", "High Energy Transfer")
            ),
            TrickShot(
                id = "chain_combo_double",
                title = "Multi-Ball Chain Shot (Combo)",
                category = "Combo Shots",
                difficulty = "Master",
                description = "Direct the striker into Puck A, causing Puck A to strike Puck B along their contact line directly into the top pocket.",
                cueTip = "Focus completely on aligning the collision normal between Puck A and Puck B with the target pocket, then hit Puck A full-face.",
                initialStrikerPos = Vector2D(cx - 60f, bY),
                pucks = listOf(
                    Puck(5, Vector2D(cx - 30f, cy + 50f), type = PuckType.WHITE),
                    Puck(6, Vector2D(cx + 30f, cy - 80f), type = PuckType.QUEEN)
                ),
                targetPocketIndex = 1, // Top-Right
                recommendedAngle = 55.0f,
                recommendedPower = 78,
                keyConcepts = listOf("Momentum Conservation", "Secondary Impact Vector", "Pocket Alignment Angle")
            ),
            TrickShot(
                id = "double_cushion_v",
                title = "Double Cushion V-Shot",
                category = "Cushion & Rebound",
                difficulty = "Master",
                description = "Hit the puck or striker to strike two adjacent cushions in a V-pattern to navigate around blocking pucks.",
                cueTip = "Requires 82% power to compensate for double-cushion friction loss. Target 1/3 point on the top cushion.",
                initialStrikerPos = Vector2D(board.baselineRightX - 40f, bY),
                pucks = listOf(
                    Puck(7, Vector2D(cx + 100f, cy - 120f), type = PuckType.WHITE),
                    Puck(8, Vector2D(cx, cy), type = PuckType.BLACK)
                ),
                targetPocketIndex = 0, // Top-Left
                recommendedAngle = 135.0f,
                recommendedPower = 84,
                keyConcepts = listOf("Two-Rail Geometry", "Velocity Loss Compensation", "Obstacle Clearance")
            )
        )
    }
}
