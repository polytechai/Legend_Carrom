package com.example.physics

import com.example.model.BoardGeometry
import com.example.model.Pocket
import com.example.model.Puck
import com.example.model.PuckType
import com.example.model.TrajectoryNode
import com.example.model.TrajectoryNodeType
import com.example.model.TrajectoryResult
import com.example.model.TrajectorySegment
import com.example.model.Vector2D
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * High-performance 2D Physics Calculator for Carrom Disc Pool.
 * Computes:
 * - Direct striker-to-puck elastic collision trajectories
 * - Cushion reflections (Angle of Incidence = Angle of Reflection)
 * - Multi-ball chain collision branches (combo shots)
 * - Bank shots (striker cushion bounce before hitting puck)
 * - Pocket alignment confidence & recommended angle/power
 */
class PhysicsCalculator(
    private val board: BoardGeometry = BoardGeometry()
) {

    /**
     * Calculates complete trajectory paths given striker position and aim direction.
     */
    fun calculateTrajectory(
        strikerPos: Vector2D,
        aimDirection: Vector2D,
        pucks: List<Puck>,
        maxBounces: Int = 2,
        allowSecondaryCollision: Boolean = true
    ): TrajectoryResult {
        val dir = aimDirection.normalized()
        if (dir.lengthSquared() < 0.0001f) return TrajectoryResult()

        val activePucks = pucks.filter { !it.isPotted && it.type != PuckType.STRIKER }
        val nodes = mutableListOf<TrajectoryNode>()
        val strikerSegments = mutableListOf<TrajectorySegment>()
        val puckSegments = mutableListOf<TrajectorySegment>()
        val secondarySegments = mutableListOf<TrajectorySegment>()

        nodes.add(TrajectoryNode(strikerPos, TrajectoryNodeType.STRIKER_START, "Striker"))

        // 1. Raycast striker to first puck or cushion
        val firstPuckHit = findEarliestPuckCollision(
            origin = strikerPos,
            dir = dir,
            radius = board.strikerRadius,
            pucks = activePucks
        )

        var alignedPocket: Pocket? = null
        var pocketConfidence = 0f
        var targetPuck: Puck? = null
        var secondaryPuck: Puck? = null
        var contactGhostPos: Vector2D? = null
        var hasCushionRebound = false
        var cutAngleDeg = 0f

        if (firstPuckHit != null) {
            // Direct hit on a puck
            targetPuck = firstPuckHit.puck
            val ghostPos = strikerPos + dir * firstPuckHit.distance
            contactGhostPos = ghostPos

            // Striker segment to ghost ball
            strikerSegments.add(TrajectorySegment(strikerPos, ghostPos))
            nodes.add(TrajectoryNode(ghostPos, TrajectoryNodeType.PUCK_CONTACT, "Contact Point", radius = board.strikerRadius))

            // Collision normal (Center of ghost striker to Center of target puck)
            val normal = (targetPuck.position - ghostPos).normalized()
            val tangent = Vector2D(-normal.y, normal.x)

            // Striker cut angle
            val dotProduct = dir.dot(normal).coerceIn(-1f, 1f)
            cutAngleDeg = Math.toDegrees(Math.acos(dotProduct.toDouble())).toFloat()

            // Striker deflection segment (deflects along tangent)
            val strikerDeflectMag = abs(dir.dot(tangent)) * 140f
            if (strikerDeflectMag > 10f) {
                val deflectDir = if (dir.dot(tangent) >= 0) tangent else tangent * -1f
                strikerSegments.add(TrajectorySegment(ghostPos, ghostPos + deflectDir * strikerDeflectMag))
            }

            // Target puck trajectory along collision normal
            var puckRayOrigin = targetPuck.position
            var puckRayDir = normal

            // Check if target puck hits a secondary puck (Chain shot / combo)
            val remainingPucks = activePucks.filter { it.id != targetPuck.id }
            val secondaryHit = if (allowSecondaryCollision) {
                findEarliestPuckCollision(
                    origin = puckRayOrigin,
                    dir = puckRayDir,
                    radius = board.puckRadius,
                    pucks = remainingPucks
                )
            } else null

            if (secondaryHit != null) {
                secondaryPuck = secondaryHit.puck
                val secGhost = puckRayOrigin + puckRayDir * secondaryHit.distance
                puckSegments.add(TrajectorySegment(puckRayOrigin, secGhost, isPuckPath = true))
                nodes.add(TrajectoryNode(secGhost, TrajectoryNodeType.SECONDARY_CONTACT, "Combo Impact"))

                // Secondary puck direction
                val secNormal = (secondaryPuck.position - secGhost).normalized()
                val secEnd = calculateCushionOrPocketPath(
                    start = secondaryPuck.position,
                    dir = secNormal,
                    puckRadius = board.puckRadius,
                    maxBounces = 1,
                    segments = secondarySegments,
                    nodes = nodes
                )
                alignedPocket = secEnd.pocket
                pocketConfidence = secEnd.confidence
            } else {
                // Puck proceeds towards pocket or cushions
                val pathResult = calculateCushionOrPocketPath(
                    start = puckRayOrigin,
                    dir = puckRayDir,
                    puckRadius = board.puckRadius,
                    maxBounces = maxBounces,
                    segments = puckSegments,
                    nodes = nodes
                )
                alignedPocket = pathResult.pocket
                pocketConfidence = pathResult.confidence
                hasCushionRebound = pathResult.hasCushionBounce
            }
        } else {
            // Striker misses all pucks initially -> Hits cushion (Bank Shot)
            val cushionHit = raycastCushion(strikerPos, dir, board.strikerRadius)
            if (cushionHit != null) {
                strikerSegments.add(TrajectorySegment(strikerPos, cushionHit.point))
                nodes.add(TrajectoryNode(cushionHit.point, TrajectoryNodeType.CUSHION_BOUNCE, "Bank Cushion"))
                hasCushionRebound = true

                // Rebound direction
                val reboundDir = dir.reflect(cushionHit.normal)

                // Check if rebounded striker hits a puck
                val bankPuckHit = findEarliestPuckCollision(
                    origin = cushionHit.point,
                    dir = reboundDir,
                    radius = board.strikerRadius,
                    pucks = activePucks
                )

                if (bankPuckHit != null) {
                    targetPuck = bankPuckHit.puck
                    val bankGhost = cushionHit.point + reboundDir * bankPuckHit.distance
                    contactGhostPos = bankGhost
                    strikerSegments.add(TrajectorySegment(cushionHit.point, bankGhost, isCushionBounce = true))
                    nodes.add(TrajectoryNode(bankGhost, TrajectoryNodeType.PUCK_CONTACT, "Bank Contact"))

                    val bankNormal = (targetPuck.position - bankGhost).normalized()
                    val pathResult = calculateCushionOrPocketPath(
                        start = targetPuck.position,
                        dir = bankNormal,
                        puckRadius = board.puckRadius,
                        maxBounces = 1,
                        segments = puckSegments,
                        nodes = nodes
                    )
                    alignedPocket = pathResult.pocket
                    pocketConfidence = pathResult.confidence
                } else {
                    // Second cushion bounce for striker
                    val secondCushion = raycastCushion(cushionHit.point, reboundDir, board.strikerRadius)
                    if (secondCushion != null) {
                        strikerSegments.add(TrajectorySegment(cushionHit.point, secondCushion.point, isCushionBounce = true))
                        nodes.add(TrajectoryNode(secondCushion.point, TrajectoryNodeType.CUSHION_BOUNCE, "2nd Rail"))
                    } else {
                        strikerSegments.add(TrajectorySegment(cushionHit.point, cushionHit.point + reboundDir * 400f, isCushionBounce = true))
                    }
                }
            } else {
                strikerSegments.add(TrajectorySegment(strikerPos, strikerPos + dir * 600f))
            }
        }

        // Shot angle in standard coordinates
        val shotAngleDeg = dir.angleDegrees()

        // Recommended power percentage based on travel distance and collisions
        val totalTravelDist = strikerSegments.sumOf { (it.end - it.start).length().toDouble() }.toFloat() +
                puckSegments.sumOf { (it.end - it.start).length().toDouble() }.toFloat()
        val basePower = ((totalTravelDist / 1200f) * 70f + 25f).coerceIn(30f, 95f)
        val recommendedPower = (basePower * (if (hasCushionRebound) 1.2f else 1.0f)).toInt().coerceIn(30, 98)

        return TrajectoryResult(
            strikerPath = strikerSegments,
            targetPuckPath = puckSegments,
            secondaryPuckPath = secondarySegments,
            nodes = nodes,
            contactGhostPuckPos = contactGhostPos,
            targetPuck = targetPuck,
            secondaryPuck = secondaryPuck,
            alignedPocket = alignedPocket,
            pocketConfidence = pocketConfidence,
            shotAngleDegrees = shotAngleDeg,
            recommendedPower = recommendedPower,
            cutAngleDegrees = cutAngleDeg,
            hasCushionRebound = hasCushionRebound
        )
    }

    /**
     * Traces a puck's path through cushions, checking for pocket entrances along the way.
     */
    private fun calculateCushionOrPocketPath(
        start: Vector2D,
        dir: Vector2D,
        puckRadius: Float,
        maxBounces: Int,
        segments: MutableList<TrajectorySegment>,
        nodes: MutableList<TrajectoryNode>
    ): PathTraceResult {
        var currentOrigin = start
        var currentDir = dir.normalized()
        var bestPocket: Pocket? = null
        var maxConfidence = 0f
        var didBounce = false

        for (bounce in 0..maxBounces) {
            val cushionHit = raycastCushion(currentOrigin, currentDir, puckRadius)
            if (cushionHit == null) break

            // Check if line to cushion intersects any corner pocket
            val pocketAlignment = checkPocketDirectAlignment(currentOrigin, currentDir, cushionHit.distance)
            if (pocketAlignment != null && pocketAlignment.confidence > 60f) {
                segments.add(TrajectorySegment(currentOrigin, pocketAlignment.pocket.position, isPuckPath = true, isCushionBounce = (bounce > 0), targetPocket = pocketAlignment.pocket))
                nodes.add(TrajectoryNode(pocketAlignment.pocket.position, TrajectoryNodeType.POCKET_DESTINATION, pocketAlignment.pocket.name))
                return PathTraceResult(pocketAlignment.pocket, pocketAlignment.confidence, didBounce)
            }

            // Segment to cushion
            segments.add(TrajectorySegment(currentOrigin, cushionHit.point, isPuckPath = true, isCushionBounce = (bounce > 0)))
            nodes.add(TrajectoryNode(cushionHit.point, TrajectoryNodeType.CUSHION_BOUNCE, "Rebound ${bounce + 1}"))
            didBounce = true

            // Reflect
            currentOrigin = cushionHit.point
            currentDir = currentDir.reflect(cushionHit.normal)
        }

        // Check if final direction points to a pocket
        val finalCheck = checkPocketDirectAlignment(currentOrigin, currentDir, 1000f)
        if (finalCheck != null) {
            bestPocket = finalCheck.pocket
            maxConfidence = finalCheck.confidence
        }

        return PathTraceResult(bestPocket, maxConfidence, didBounce)
    }

    /**
     * Determines whether a raycast directly enters one of the 4 corner pockets.
     */
    private fun checkPocketDirectAlignment(
        origin: Vector2D,
        dir: Vector2D,
        maxDist: Float
    ): PocketAlignment? {
        var best: PocketAlignment? = null
        var highestScore = 0f

        for (pocket in board.pockets) {
            val toPocket = pocket.position - origin
            val dist = toPocket.length()
            if (dist > maxDist + pocket.radius) continue

            val proj = toPocket.dot(dir)
            if (proj <= 0) continue

            val perpDistSq = toPocket.lengthSquared() - proj * proj
            val pocketRadiusSq = pocket.radius * pocket.radius

            if (perpDistSq < pocketRadiusSq) {
                val perpDist = sqrt(perpDistSq.coerceAtLeast(0f))
                val score = ((1f - (perpDist / pocket.radius)) * 100f).coerceIn(0f, 100f)
                if (score > highestScore) {
                    highestScore = score
                    best = PocketAlignment(pocket, score)
                }
            }
        }
        return best
    }

    /**
     * Raycasts a circular body against board cushions.
     */
    fun raycastCushion(origin: Vector2D, dir: Vector2D, radius: Float): CushionHit? {
        val minX = board.playLeft + radius
        val maxX = board.playRight - radius
        val minY = board.playTop + radius
        val maxY = board.playBottom - radius

        var closestT = Float.MAX_VALUE
        var hitPoint: Vector2D? = null
        var hitNormal: Vector2D? = null

        // Left cushion
        if (dir.x < -0.0001f) {
            val t = (minX - origin.x) / dir.x
            val y = origin.y + t * dir.y
            if (t > 0.01f && t < closestT && y in minY..maxY) {
                closestT = t
                hitPoint = Vector2D(minX, y)
                hitNormal = Vector2D(1f, 0f)
            }
        }
        // Right cushion
        if (dir.x > 0.0001f) {
            val t = (maxX - origin.x) / dir.x
            val y = origin.y + t * dir.y
            if (t > 0.01f && t < closestT && y in minY..maxY) {
                closestT = t
                hitPoint = Vector2D(maxX, y)
                hitNormal = Vector2D(-1f, 0f)
            }
        }
        // Top cushion
        if (dir.y < -0.0001f) {
            val t = (minY - origin.y) / dir.y
            val x = origin.x + t * dir.x
            if (t > 0.01f && t < closestT && x in minX..maxX) {
                closestT = t
                hitPoint = Vector2D(x, minY)
                hitNormal = Vector2D(0f, 1f)
            }
        }
        // Bottom cushion
        if (dir.y > 0.0001f) {
            val t = (maxY - origin.y) / dir.y
            val x = origin.x + t * dir.x
            if (t > 0.01f && t < closestT && x in minX..maxX) {
                closestT = t
                hitPoint = Vector2D(x, maxY)
                hitNormal = Vector2D(0f, -1f)
            }
        }

        return if (hitPoint != null && hitNormal != null) {
            CushionHit(hitPoint, hitNormal, closestT)
        } else null
    }

    /**
     * Finds the closest puck intersected by a moving circle of given radius along direction ray.
     */
    private fun findEarliestPuckCollision(
        origin: Vector2D,
        dir: Vector2D,
        radius: Float,
        pucks: List<Puck>
    ): PuckHit? {
        var minDistance = Float.MAX_VALUE
        var hitPuck: Puck? = null

        for (puck in pucks) {
            val combinedRadius = radius + puck.radius
            val toPuck = puck.position - origin
            val projection = toPuck.dot(dir)

            if (projection <= 0.01f) continue // Puck is behind ray

            val distSq = toPuck.lengthSquared() - projection * projection
            val combRadiusSq = combinedRadius * combinedRadius

            if (distSq <= combRadiusSq) {
                val offset = sqrt((combRadiusSq - distSq).coerceAtLeast(0f))
                val hitDistance = projection - offset
                if (hitDistance > 0.01f && hitDistance < minDistance) {
                    minDistance = hitDistance
                    hitPuck = puck
                }
            }
        }

        return if (hitPuck != null) PuckHit(hitPuck, minDistance) else null
    }

    data class CushionHit(val point: Vector2D, val normal: Vector2D, val distance: Float)
    private data class PuckHit(val puck: Puck, val distance: Float)
    private data class PocketAlignment(val pocket: Pocket, val confidence: Float)
    private data class PathTraceResult(val pocket: Pocket?, val confidence: Float, val hasCushionBounce: Boolean)
}
