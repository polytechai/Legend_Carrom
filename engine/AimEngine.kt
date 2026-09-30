package com.example.engine

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector2(val x: Float, val y: Float) {
    operator fun plus(v: Vector2) = Vector2(x + v.x, y + v.y)
    operator fun minus(v: Vector2) = Vector2(x - v.x, y - v.y)
    operator fun times(scalar: Float) = Vector2(x * scalar, y * scalar)
    operator fun div(scalar: Float) = Vector2(x / scalar, y / scalar)

    fun dot(v: Vector2): Float = x * v.x + y * v.y
    fun length(): Float = sqrt(x * x + y * y)
    fun lengthSquared(): Float = x * x + y * y

    fun normalized(): Vector2 {
        val len = length()
        return if (len > 0.0001f) Vector2(x / len, y / len) else Vector2(0f, 0f)
    }

    fun reflect(normal: Vector2): Vector2 {
        val d = this.dot(normal)
        return this - normal * (2f * d)
    }

    fun angleDegrees(): Float {
        var deg = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
        if (deg < 0) deg += 360f
        return deg
    }

    companion object {
        fun fromAngle(deg: Float): Vector2 {
            val rad = Math.toRadians(deg.toDouble())
            return Vector2(cos(rad).toFloat(), sin(rad).toFloat())
        }
    }
}

data class BoardBounds(
    val left: Float = 60f,
    val top: Float = 60f,
    val right: Float = 1020f,
    val bottom: Float = 1020f,
    val strikerRadius: Float = 28f,
    val puckRadius: Float = 24f,
    val pocketRadius: Float = 48f
) {
    val pockets: List<Vector2> = listOf(
        Vector2(left + pocketRadius * 0.8f, top + pocketRadius * 0.8f),       // Top-Left
        Vector2(right - pocketRadius * 0.8f, top + pocketRadius * 0.8f),      // Top-Right
        Vector2(left + pocketRadius * 0.8f, bottom - pocketRadius * 0.8f),    // Bottom-Left
        Vector2(right - pocketRadius * 0.8f, bottom - pocketRadius * 0.8f)    // Bottom-Right
    )
}

data class AimTrajectory(
    val strikerPath: List<Vector2>,
    val targetPuckPath: List<Vector2>,
    val ghostStrikerPos: Vector2?,
    val targetPuckIndex: Int = -1,
    val targetPocket: Vector2?,
    val pocketConfidence: Float = 0f,
    val powerPercent: Float = 0f,
    val cutAngleDegrees: Float = 0f,
    val isVisible: Boolean = true
)

/**
 * 2D Precision Vector Calculation Engine for Carrom Trajectories.
 * Features:
 * - Dynamic line length proportional to drag distance (0% to 100% power)
 * - Precise cut angles and deflection tangents
 * - Puck ricochet vectors to cushions and pockets
 */
class AimEngine(private val bounds: BoardBounds = BoardBounds()) {

    companion object {
        const val MAX_TRAVEL_DISTANCE = 1100f
    }

    /**
     * Calculates trajectory paths dynamically mapped to power level (0% - 100%).
     *
     * @param strikerPos Current striker coordinates
     * @param aimDirection Forward normalized aim vector
     * @param puckPositions List of active puck coordinates
     * @param powerPercent Current shot power (0f to 100f) derived from drag distance
     * @param maxReflections Maximum cushion bounces to compute for puck
     */
    fun calculateAim(
        strikerPos: Vector2,
        aimDirection: Vector2,
        puckPositions: List<Vector2>,
        powerPercent: Float,
        maxReflections: Int = 1
    ): AimTrajectory {
        val clampedPower = powerPercent.coerceIn(0f, 100f)
        if (clampedPower < 1f) {
            return AimTrajectory(emptyList(), emptyList(), null, -1, null, 0f, 0f, 0f, isVisible = false)
        }

        val dir = aimDirection.normalized()
        if (dir.lengthSquared() < 0.0001f) {
            return AimTrajectory(emptyList(), emptyList(), null, -1, null, 0f, 0f, 0f, isVisible = false)
        }

        // Available travel distance budget based on power level
        val maxStrikerDistance = (clampedPower / 100f) * MAX_TRAVEL_DISTANCE

        val strikerPath = mutableListOf<Vector2>()
        val puckPath = mutableListOf<Vector2>()
        strikerPath.add(strikerPos)

        // Find earliest puck collision along ray
        var earliestHitDist = Float.MAX_VALUE
        var hitPuckIndex = -1
        val collisionRadius = bounds.strikerRadius + bounds.puckRadius

        for (i in puckPositions.indices) {
            val toPuck = puckPositions[i] - strikerPos
            val projection = toPuck.dot(dir)
            if (projection <= 0.01f) continue

            val perpDistSq = toPuck.lengthSquared() - projection * projection
            val radiusSq = collisionRadius * collisionRadius

            if (perpDistSq <= radiusSq) {
                val offset = sqrt((radiusSq - perpDistSq).coerceAtLeast(0f))
                val hitDistance = projection - offset
                if (hitDistance in 0.01f..earliestHitDist) {
                    earliestHitDist = hitDistance
                    hitPuckIndex = i
                }
            }
        }

        var ghostPos: Vector2? = null
        var bestPocket: Vector2? = null
        var bestConfidence = 0f
        var cutAngle = 0f

        // Check if striker reaches the puck with current power
        if (hitPuckIndex != -1 && earliestHitDist <= maxStrikerDistance) {
            val ghost = strikerPos + dir * earliestHitDist
            ghostPos = ghost
            strikerPath.add(ghost)

            val targetPuck = puckPositions[hitPuckIndex]
            val collisionNormal = (targetPuck - ghost).normalized()

            // Calculate deflection tangent
            val tangent = Vector2(-collisionNormal.y, collisionNormal.x)
            val cutDot = dir.dot(collisionNormal).coerceIn(-1f, 1f)
            cutAngle = Math.toDegrees(acos(cutDot.toDouble())).toFloat()

            // Remaining power transferred to target puck
            val remainingDistBudget = (maxStrikerDistance - earliestHitDist) * cutDot.coerceAtLeast(0.1f)

            // Striker deflecting trajectory
            val deflectionMagnitude = abs(dir.dot(tangent)) * (maxStrikerDistance - earliestHitDist) * 0.4f
            if (deflectionMagnitude > 5f) {
                val deflectDir = if (dir.dot(tangent) >= 0) tangent else tangent * -1f
                strikerPath.add(ghost + deflectDir * deflectionMagnitude)
            }

            // Puck path along collision normal scaled by remaining power
            if (remainingDistBudget > 10f) {
                puckPath.add(targetPuck)
                val puckRayEnd = tracePuckToPocketOrRail(
                    start = targetPuck,
                    dir = collisionNormal,
                    radius = bounds.puckRadius,
                    remainingDist = remainingDistBudget,
                    maxBounces = maxReflections,
                    outPoints = puckPath
                )
                bestPocket = puckRayEnd.pocket
                bestConfidence = puckRayEnd.confidence
            }
        } else {
            // Striker does not hit any puck within current power distance
            val cushionHit = raycastRail(strikerPos, dir, bounds.strikerRadius)
            if (cushionHit != null && cushionHit.distance <= maxStrikerDistance) {
                strikerPath.add(cushionHit.hitPoint)
                val remainingDistAfterBounce = maxStrikerDistance - cushionHit.distance
                if (remainingDistAfterBounce > 5f) {
                    val reboundDir = dir.reflect(cushionHit.normal)
                    strikerPath.add(cushionHit.hitPoint + reboundDir * remainingDistAfterBounce)
                }
            } else {
                strikerPath.add(strikerPos + dir * maxStrikerDistance)
            }
        }

        return AimTrajectory(
            strikerPath = strikerPath,
            targetPuckPath = puckPath,
            ghostStrikerPos = ghostPos,
            targetPuckIndex = hitPuckIndex,
            targetPocket = bestPocket,
            pocketConfidence = bestConfidence,
            powerPercent = clampedPower,
            cutAngleDegrees = cutAngle,
            isVisible = true
        )
    }

    private fun tracePuckToPocketOrRail(
        start: Vector2,
        dir: Vector2,
        radius: Float,
        remainingDist: Float,
        maxBounces: Int,
        outPoints: MutableList<Vector2>
    ): TraceResult {
        var currentOrigin = start
        var currentDir = dir.normalized()
        var currentRemaining = remainingDist
        var matchedPocket: Vector2? = null
        var confidence = 0f

        for (bounce in 0..maxBounces) {
            val railHit = raycastRail(currentOrigin, currentDir, radius)
            val stepDist = railHit?.distance ?: currentRemaining

            // Check if trajectory intersects any pocket before hitting the rail
            for (pocket in bounds.pockets) {
                val toPocket = pocket - currentOrigin
                val proj = toPocket.dot(currentDir)
                if (proj in 0f..minOf(stepDist, currentRemaining)) {
                    val perpSq = toPocket.lengthSquared() - proj * proj
                    if (perpSq <= bounds.pocketRadius * bounds.pocketRadius) {
                        outPoints.add(pocket)
                        val distToCenter = sqrt(perpSq)
                        confidence = ((1f - (distToCenter / bounds.pocketRadius)) * 100f).coerceIn(40f, 100f)
                        return TraceResult(pocket, confidence)
                    }
                }
            }

            if (railHit == null || railHit.distance > currentRemaining) {
                outPoints.add(currentOrigin + currentDir * currentRemaining)
                break
            }

            outPoints.add(railHit.hitPoint)
            currentRemaining -= railHit.distance
            currentOrigin = railHit.hitPoint
            currentDir = currentDir.reflect(railHit.normal)

            if (currentRemaining <= 5f) break
        }

        return TraceResult(matchedPocket, confidence)
    }

    private fun raycastRail(origin: Vector2, dir: Vector2, radius: Float): RailHit? {
        val minX = bounds.left + radius
        val maxX = bounds.right - radius
        val minY = bounds.top + radius
        val maxY = bounds.bottom - radius

        var closestT = Float.MAX_VALUE
        var hitPoint: Vector2? = null
        var hitNormal: Vector2? = null

        if (dir.x < -0.0001f) {
            val t = (minX - origin.x) / dir.x
            val y = origin.y + t * dir.y
            if (t > 0.01f && t < closestT && y in minY..maxY) {
                closestT = t
                hitPoint = Vector2(minX, y)
                hitNormal = Vector2(1f, 0f)
            }
        }
        if (dir.x > 0.0001f) {
            val t = (maxX - origin.x) / dir.x
            val y = origin.y + t * dir.y
            if (t > 0.01f && t < closestT && y in minY..maxY) {
                closestT = t
                hitPoint = Vector2(maxX, y)
                hitNormal = Vector2(-1f, 0f)
            }
        }
        if (dir.y < -0.0001f) {
            val t = (minY - origin.y) / dir.y
            val x = origin.x + t * dir.x
            if (t > 0.01f && t < closestT && x in minX..maxX) {
                closestT = t
                hitPoint = Vector2(x, minY)
                hitNormal = Vector2(0f, 1f)
            }
        }
        if (dir.y > 0.0001f) {
            val t = (maxY - origin.y) / dir.y
            val x = origin.x + t * dir.x
            if (t > 0.01f && t < closestT && x in minX..maxX) {
                closestT = t
                hitPoint = Vector2(x, maxY)
                hitNormal = Vector2(0f, -1f)
            }
        }

        return if (hitPoint != null && hitNormal != null) RailHit(hitPoint, hitNormal, closestT) else null
    }

    private data class RailHit(val hitPoint: Vector2, val normal: Vector2, val distance: Float)
    private data class TraceResult(val pocket: Vector2?, val confidence: Float)
}
