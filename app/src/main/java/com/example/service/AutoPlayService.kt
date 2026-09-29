package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class AutoPlayService : AccessibilityService() {

    companion object {
        var instance: AutoPlayService? = null
            private set

        fun isAccessibilityEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event monitoring if required
    }

    override fun onInterrupt() {
        // Handling interruption
    }

    /**
     * Dispatches a smooth simulated swipe gesture across the screen.
     * @param fromX Starting screen X
     * @param fromY Starting screen Y
     * @param toX Ending screen X
     * @param toY Ending screen Y
     * @param durationMs Duration of stroke in milliseconds
     */
    fun performAimStroke(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        durationMs: Long = 300,
        onComplete: (() -> Unit)? = null
    ) {
        val swipePath = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }

        val stroke = GestureDescription.StrokeDescription(swipePath, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
            }
        }, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
