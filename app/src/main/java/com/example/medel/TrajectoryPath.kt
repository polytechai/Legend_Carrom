package com.example.model

/**
 * Node types rendered along trajectory paths.
 */
enum class TrajectoryNodeType {
    STRIKER_START,
    PUCK_CONTACT,
    CUSHION_BOUNCE,
    POCKET_DESTINATION,
    SECONDARY_CONTACT
}

data class TrajectoryNode(
    val position: Vector2D,
    val type: TrajectoryNodeType,
    val label: String = "",
    val radius: Float = 14f
)

data class TrajectorySegment(
    val start: Vector2D,
    val end: Vector2D,
    val isCushionBounce: Boolean = false,
    val isPuckPath: Boolean = false,
    val isSecondaryPuckPath: Boolean = false,
    val targetPocket: Pocket? = null
)

data class TrajectoryResult(
    val strikerPath: List<TrajectorySegment> = emptyList(),
    val targetPuckPath: List<TrajectorySegment> = emptyList(),
    val secondaryPuckPath: List<TrajectorySegment> = emptyList(),
    val nodes: List<TrajectoryNode> = emptyList(),
    val contactGhostPuckPos: Vector2D? = null,
    val targetPuck: Puck? = null,
    val secondaryPuck: Puck? = null,
    val alignedPocket: Pocket? = null,
    val pocketConfidence: Float = 0f, // 0 to 100%
    val shotAngleDegrees: Float = 0f,
    val recommendedPower: Int = 0, // 0 to 100%
    val cutAngleDegrees: Float = 0f,
    val hasCushionRebound: Boolean = false
)
