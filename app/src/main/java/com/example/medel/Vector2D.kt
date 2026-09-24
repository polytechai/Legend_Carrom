package com.example.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D Vector representation for Carrom physics and trajectory calculations.
 */
data class Vector2D(
    val x: Float,
    val y: Float
) {
    operator fun plus(other: Vector2D): Vector2D = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D): Vector2D = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Float): Vector2D = Vector2D(x * scalar, y * scalar)
    operator fun div(scalar: Float): Vector2D = if (scalar != 0f) Vector2D(x / scalar, y / scalar) else Vector2D(0f, 0f)

    fun dot(other: Vector2D): Float = x * other.x + y * other.y
    fun cross(other: Vector2D): Float = x * other.y - y * other.x

    fun lengthSquared(): Float = x * x + y * y
    fun length(): Float = sqrt(lengthSquared())

    fun distanceTo(other: Vector2D): Float = (this - other).length()
    fun distanceSquaredTo(other: Vector2D): Float = (this - other).lengthSquared()

    fun normalized(): Vector2D {
        val len = length()
        return if (len > 0.0001f) Vector2D(x / len, y / len) else Vector2D(0f, 0f)
    }

    /**
     * Angle in degrees from positive X axis (-180 to 180).
     */
    fun angleDegrees(): Float {
        val deg = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
        return if (deg < 0) deg + 360f else deg
    }

    /**
     * Reflect vector against a normal surface vector n (assumed unit normal).
     */
    fun reflect(normal: Vector2D): Vector2D {
        val n = normal.normalized()
        val dot = this.dot(n)
        return this - n * (2f * dot)
    }

    companion object {
        val ZERO = Vector2D(0f, 0f)
        fun fromAngle(angleDegrees: Float, length: Float = 1f): Vector2D {
            val rad = Math.toRadians(angleDegrees.toDouble())
            return Vector2D((cos(rad) * length).toFloat(), (sin(rad) * length).toFloat())
        }
    }
}
