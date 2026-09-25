package com.example.model

enum class PuckType {
    STRIKER,
    WHITE,
    BLACK,
    QUEEN
}

data class Puck(
    val id: Int,
    var position: Vector2D,
    var velocity: Vector2D = Vector2D.ZERO,
    val radius: Float = 24f,
    val type: PuckType = PuckType.WHITE,
    var isPotted: Boolean = false,
    val mass: Float = if (type == PuckType.STRIKER) 2.5f else 1.0f
) {
    fun copyWithPos(newPos: Vector2D): Puck = copy(position = newPos)
}
