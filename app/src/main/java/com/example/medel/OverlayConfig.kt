package com.example.model

import android.graphics.Color

data class OverlayConfig(
    val strokeWidthDp: Float = 4.5f,
    val strikerLineColor: Int = Color.parseColor("#00E5FF"), // Electric Cyan
    val puckLineColor: Int = Color.parseColor("#FFD600"),    // Vibrant Gold
    val cushionLineColor: Int = Color.parseColor("#FF4081"), // Hot Pink
    val secondaryLineColor: Int = Color.parseColor("#00E676"),// Neon Green
    val ghostCircleColor: Int = Color.argb(160, 255, 255, 255),
    val showCushionBounces: Boolean = true,
    val maxCushionBounces: Int = 2,
    val showSecondaryCollisions: Boolean = true,
    val showAnglePowerMeter: Boolean = true,
    val showGhostPuck: Boolean = true,
    val showPocketConfidence: Boolean = true,
    val isOverlayEnabled: Boolean = true
)
