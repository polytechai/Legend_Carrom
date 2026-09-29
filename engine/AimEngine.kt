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
    val recommendedPowerPercent: Int = 50,
    val cutAngleDegrees: Float = 0f
)

class AimEngine(private val bounds: BoardBounds = BoardBounds()) {

    /**
     * Computes full strike prediction given striker position, drag vector, and detected pucks.
     */
    fun calculateAim(
        strikerPos: Vector2,
        aimDirection: Vector2,
        puckPositions: List<Vector2>,
        maxReflections: Int = 1
    ): AimTrajectory {
        val dir = aimDirection.normalized()
        if (dir.lengthSquared() < 0.0001f) {
            return AimTrajectory(emptyList(), emptyList(), null, -1, null)
        }

        val strikerPath = mutableListOf<Vector2>()
        val puckPath = mutableListOf<Vector2>()
        strikerPath.add(strikerPos)

        // Find earliest puck collision
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

        if (hitPuckIndex != -1) {
            // Direct hit on a puck
            val ghost = strikerPos + dir * earliestHitDist
            ghostPos = ghost
            strikerPath.add(ghost)

            val targetPuck = puckPositions[hitPuckIndex]
            val collisionNormal = (targetPuck - ghost).normalized()

            // Calculate deflection tangent
            val tangent = Vector2(-collisionNormal.y, collisionNormal.x)
            val cutDot = dir.dot(collisionNormal).coerceIn(-1f, 1f)
            cutAngle = Math.toDegrees(acos(cutDot.toDouble())).toFloat()

            // Striker deflecting trajectory
            val deflectionMagnitude = abs(dir.dot(tangent)) * 120f
            if (deflectionMagnitude > 5f) {
                val deflectDir = if (dir.dot(tangent) >= 0) tangent else tangent * -1f
                strikerPath.add(ghost + deflectDir * deflectionMagnitude)
            }

            // Puck path along collision normal
            puckPath.add(targetPuck)
            val puckRayEnd = tracePuckToPocketOrRail(targetPuck, collisionNormal, bounds.puckRadius, maxReflections, puckPath)
            bestPocket = puckRayEnd.pocket
            bestConfidence = puckRayEnd.confidence
        } else {
            // Misses all pucks: rail reflection (Bank shot)
            val cushionHit = raycastRail(strikerPos, dir, bounds.strikerRadius)
            if (cushionHit != null) {
                strikerPath.add(cushionHit.hitPoint)
                val reboundDir = dir.reflect(cushionHit.normal)
                strikerPath.add(cushionHit.hitPoint + reboundDir * 400f)
            } else {
                strikerPath.add(strikerPos + dir * 600f)
            }
        }

        val totalDistance = calculateTotalDistance(strikerPath) + calculateTotalDistance(puckPath)
        val power = ((totalDistance / 1000f) * 65f + 25f).toInt().coerceIn(30, 95)

        return AimTrajectory(
            strikerPath = strikerPath,
            targetPuckPath = puckPath,
            ghostStrikerPos = ghostPos,
            targetPuckIndex = hitPuckIndex,
            targetPocket = bestPocket,
            pocketConfidence = bestConfidence,
            recommendedPowerPercent = power,
            cutAngleDegrees = cutAngle
        )
    }

    private fun tracePuckToPocketOrRail(
        start: Vector2,
        dir: Vector2,
        radius: Float,
        maxBounces: Int,
        outPoints: MutableList<Vector2>
    ): TraceResult {
        var currentOrigin = start
        var currentDir = dir.normalized()
        var matchedPocket: Vector2? = null
        var confidence = 0f

        for (bounce in 0..maxBounces) {
            val railHit = raycastRail(currentOrigin, currentDir, radius) ?: break

            // Check if trajectory intersects any pocket before hitting the rail
            for (pocket in bounds.pockets) {
                val toPocket = pocket - currentOrigin
                val proj = toPocket.dot(currentDir)
                if (proj in 0f..railHit.distance) {
                    val perpSq = toPocket.lengthSquared() - proj * proj
                    if (perpSq <= bounds.pocketRadius * bounds.pocketRadius) {
                        outPoints.add(pocket)
                        val distToCenter = sqrt(perpSq)
                        confidence = ((1f - (distToCenter / bounds.pocketRadius)) * 100f).coerceIn(40f, 100f)
                        return TraceResult(pocket, confidence)
                    }
                }
            }

            outPoints.add(railHit.hitPoint)
            currentOrigin = railHit.hitPoint
            currentDir = currentDir.reflect(railHit.normal)
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

    private fun calculateTotalDistance(path: List<Vector2>): Float {
        var total = 0f
        for (i in 0 until path.size - 1) {
            total += (path[i + 1] - path[i]).length()
        }
        return total
    }

    private data class RailHit(val hitPoint: Vector2, val normal: Vector2, val distance: Float)
    private data class TraceResult(val pocket: Vector2?, val confidence: Float)
}
