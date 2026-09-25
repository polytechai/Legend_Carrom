package com.example.model

import kotlin.math.abs

data class MistakeAnalysis(
    val idealAngle: Float,
    val actualAngle: Float,
    val idealPower: Int,
    val actualPower: Int,
    val angleDifference: Float,
    val powerDifference: Int,
    val wasPotted: Boolean,
    val feedbackTitle: String,
    val feedbackDetails: String,
    val suggestion: String,
    val accuracyScore: Int // 0 to 100
) {
    companion object {
        fun evaluate(
            idealAngle: Float,
            actualAngle: Float,
            idealPower: Int,
            actualPower: Int,
            potted: Boolean,
            targetPocket: Pocket?
        ): MistakeAnalysis {
            var diffAngle = actualAngle - idealAngle
            while (diffAngle > 180f) diffAngle -= 360f
            while (diffAngle < -180f) diffAngle += 360f

            val absAngleDiff = abs(diffAngle)
            val powerDiff = actualPower - idealPower

            val angleScore = (100f - (absAngleDiff * 12f)).coerceIn(0f, 100f)
            val powerScore = (100f - (abs(powerDiff) * 1.5f)).coerceIn(0f, 100f)
            val overallScore = ((angleScore * 0.7f) + (powerScore * 0.3f)).toInt()

            val title: String
            val details: String
            val suggestion: String

            if (potted) {
                title = "Clean Shot! Sunk Successfully"
                details = "Angle deviation was only ${"%.1f".format(absAngleDiff)}°. Power was well-controlled."
                suggestion = "Excellent muscle memory. Keep this release consistent for high pressure matches."
            } else if (absAngleDiff > 6.0f) {
                val dir = if (diffAngle > 0) "too wide (cut too thin)" else "too tight (cut too thick)"
                title = "Angle Deviation: ${"%.1f".format(absAngleDiff)}° $dir"
                details = "The collision line missed the pocket center. The puck deflected off the pocket jaws."
                suggestion = "Aim the striker's center directly through the ghost contact point towards ${targetPocket?.name ?: "the pocket"}."
            } else if (powerDiff > 25) {
                title = "Too Much Power (+${powerDiff}%)"
                details = "High velocity created high rebound bounce off the cushion/pocket jaws instead of dropping in."
                suggestion = "Drop power to ~${idealPower}%. In Carrom, soft and medium-paced shots sink much more reliably."
            } else if (powerDiff < -25) {
                title = "Under-Powered (-${abs(powerDiff)}%)"
                details = "The shot had insufficient momentum to overcome table cloth friction before reaching the pocket."
                suggestion = "Increase power to around ${idealPower}% to guarantee the puck reaches the pocket drop zone."
            } else {
                title = "Close Miss (Deviation ${"%.1f".format(absAngleDiff)}°)"
                details = "The puck struck the corner jaw. Minor alignment adjustment needed."
                suggestion = "Fine tune your release angle by 1-2 degrees towards the inner pocket edge."
            }

            return MistakeAnalysis(
                idealAngle = idealAngle,
                actualAngle = actualAngle,
                idealPower = idealPower,
                actualPower = actualPower,
                angleDifference = diffAngle,
                powerDifference = powerDiff,
                wasPotted = potted,
                feedbackTitle = title,
                feedbackDetails = details,
                suggestion = suggestion,
                accuracyScore = overallScore
            )
        }
    }
}
