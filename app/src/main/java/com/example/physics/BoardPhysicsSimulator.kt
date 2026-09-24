package com.example.physics

import com.example.model.BoardGeometry
import com.example.model.Pocket
import com.example.model.Puck
import com.example.model.PuckType
import com.example.model.Vector2D
import kotlin.math.sqrt

/**
 * 2D Real-time Physics Simulation loop for the Offline Practice Simulator.
 * Simulates real Carrom Disc Pool board dynamics:
 * - Table cloth friction and linear deceleration
 * - Elastic puck-puck collisions with mass transfer (Striker mass > Puck mass)
 * - Cushion rebounds with coefficient of restitution
 * - Pocket falling detection
 */
class BoardPhysicsSimulator(
    val board: BoardGeometry = BoardGeometry(),
    var friction: Float = 0.022f,
    var cushionRestitution: Float = 0.86f,
    var puckRestitution: Float = 0.92f
) {

    /**
     * Executes one discrete physics time step dt.
     * Returns true if any puck or striker is still in motion.
     */
    fun step(pucks: MutableList<Puck>, striker: Puck): Boolean {
        val allEntities = mutableListOf<Puck>()
        if (!striker.isPotted) allEntities.add(striker)
        allEntities.addAll(pucks.filter { !it.isPotted })

        var anyMoving = false

        // 1. Position integration & friction damping
        for (entity in allEntities) {
            if (entity.velocity.lengthSquared() > 0.005f) {
                anyMoving = true
                entity.position = entity.position + entity.velocity
                // Linear decay + exponential damping
                val speed = entity.velocity.length()
                val newSpeed = (speed * (1f - friction) - 0.08f).coerceAtLeast(0f)
                entity.velocity = if (newSpeed > 0.1f) entity.velocity.normalized() * newSpeed else Vector2D.ZERO
            } else {
                entity.velocity = Vector2D.ZERO
            }
        }

        // 2. Cushion collision & bouncing
        for (entity in allEntities) {
            if (entity.velocity.lengthSquared() == 0f) continue

            val minX = board.playLeft + entity.radius
            val maxX = board.playRight - entity.radius
            val minY = board.playTop + entity.radius
            val maxY = board.playBottom - entity.radius

            if (entity.position.x < minX) {
                entity.position = Vector2D(minX, entity.position.y)
                entity.velocity = Vector2D(-entity.velocity.x * cushionRestitution, entity.velocity.y * 0.96f)
            } else if (entity.position.x > maxX) {
                entity.position = Vector2D(maxX, entity.position.y)
                entity.velocity = Vector2D(-entity.velocity.x * cushionRestitution, entity.velocity.y * 0.96f)
            }

            if (entity.position.y < minY) {
                entity.position = Vector2D(entity.position.x, minY)
                entity.velocity = Vector2D(entity.velocity.x * 0.96f, -entity.velocity.y * cushionRestitution)
            } else if (entity.position.y > maxY) {
                entity.position = Vector2D(entity.position.x, maxY)
                entity.velocity = Vector2D(entity.velocity.x * 0.96f, -entity.velocity.y * cushionRestitution)
            }
        }

        // 3. Puck-puck & Striker-puck pairwise elastic collision
        val count = allEntities.size
        for (i in 0 until count) {
            val a = allEntities[i]
            for (j in i + 1 until count) {
                val b = allEntities[j]
                val delta = b.position - a.position
                val distSq = delta.lengthSquared()
                val minDistance = a.radius + b.radius

                if (distSq < minDistance * minDistance && distSq > 0.0001f) {
                    val dist = sqrt(distSq)
                    val normal = delta / dist
                    val overlap = 0.5f * (minDistance - dist)

                    // Position correction to prevent clipping/sticking
                    a.position = a.position - normal * overlap
                    b.position = b.position + normal * overlap

                    // Relative velocity along collision normal
                    val relVel = a.velocity - b.velocity
                    val normalVel = relVel.dot(normal)

                    // Only impulse if moving towards each other
                    if (normalVel > 0f) {
                        val impulse = (1f + puckRestitution) * normalVel / (1f / a.mass + 1f / b.mass)
                        a.velocity = a.velocity - normal * (impulse / a.mass)
                        b.velocity = b.velocity + normal * (impulse / b.mass)
                        anyMoving = true
                    }
                }
            }
        }

        // 4. Pocket detection: Sink pucks that fall inside pocket radius
        for (entity in allEntities) {
            for (pocket in board.pockets) {
                val distToPocket = (entity.position - pocket.position).length()
                if (distToPocket < pocket.radius * 0.9f) {
                    entity.isPotted = true
                    entity.velocity = Vector2D.ZERO
                    break
                }
            }
        }

        return anyMoving
    }

    /**
     * Resets striker to baseline with zero velocity.
     */
    fun resetStriker(striker: Puck, xPos: Float = board.centerX) {
        val clampedX = xPos.coerceIn(board.baselineLeftX, board.baselineRightX)
        striker.position = Vector2D(clampedX, board.bottomBaselineY)
        striker.velocity = Vector2D.ZERO
        striker.isPotted = false
    }
}
