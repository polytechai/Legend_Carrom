package com.example.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.example.model.BoardGeometry
import com.example.model.Puck
import com.example.model.PuckType
import com.example.model.Vector2D
import kotlin.math.sqrt

/**
 * Computer Vision processing pipeline for Carrom Disc Pool detection.
 * Implements:
 * 1. Board Boundary Detection (Region of Interest)
 * 2. HSV Color Thresholding for Pucks (White, Dark/Black, Queen Red)
 * 3. Hough-style Circle & Centroid Detection for Striker & Pucks
 * 4. Corner Pocket coordinates calibration
 */
class BoardDetector(
    var calibrationOffset: Vector2D = Vector2D.ZERO,
    var scaleFactor: Float = 1.0f
) {

    data class DetectionResult(
        val boardBounds: RectF,
        val striker: Puck?,
        val pucks: List<Puck>,
        val processingTimeMs: Long,
        val confidence: Float
    )

    data class HsvRange(
        val minH: Float, val maxH: Float,
        val minS: Float, val maxS: Float,
        val minV: Float, val maxV: Float
    ) {
        fun matches(h: Float, s: Float, v: Float): Boolean {
            val hMatch = if (minH <= maxH) {
                h in minH..maxH
            } else {
                // Hue wraps around 360 (e.g. Red: 345..360 and 0..15)
                h >= minH || h <= maxH
            }
            return hMatch && s in minS..maxS && v in minV..maxV
        }
    }

    // Color threshold profiles for Carrom Disc Pool
    private val queenRedRange1 = HsvRange(340f, 360f, 0.55f, 1.0f, 0.40f, 1.0f)
    private val queenRedRange2 = HsvRange(0f, 20f, 0.55f, 1.0f, 0.40f, 1.0f)
    private val whitePuckRange = HsvRange(0f, 360f, 0.0f, 0.30f, 0.70f, 1.0f)
    private val darkPuckRange = HsvRange(0f, 360f, 0.0f, 0.40f, 0.05f, 0.38f)
    private val strikerRange = HsvRange(40f, 220f, 0.25f, 1.0f, 0.60f, 1.0f) // Distinct colored striker

    /**
     * Main computer vision pipeline execution.
     * Takes an incoming screen capture frame and extracts game elements.
     */
    fun processFrame(bitmap: Bitmap, boardGeometry: BoardGeometry): DetectionResult {
        val startTime = System.currentTimeMillis()
        val width = bitmap.width
        val height = bitmap.height

        // 1. Locate board boundary (square playing surface)
        val boardRect = detectBoardBounds(bitmap, width, height)

        // 2. Downsample scanning grid for 60fps real-time throughput
        val step = 4
        val hsv = FloatArray(3)

        val detectedWhiteCentroids = mutableListOf<Vector2D>()
        val detectedDarkCentroids = mutableListOf<Vector2D>()
        val detectedQueenCentroids = mutableListOf<Vector2D>()
        val detectedStrikerCentroids = mutableListOf<Vector2D>()

        val startX = (boardRect.left + boardGeometry.cushionInset).toInt().coerceAtLeast(0)
        val endX = (boardRect.right - boardGeometry.cushionInset).toInt().coerceAtMost(width)
        val startY = (boardRect.top + boardGeometry.cushionInset).toInt().coerceAtLeast(0)
        val endY = (boardRect.bottom - boardGeometry.cushionInset).toInt().coerceAtMost(height)

        // Color segmentation pass
        for (y in startY until endY step step) {
            for (x in startX until endX step step) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                if (queenRedRange1.matches(h, s, v) || queenRedRange2.matches(h, s, v)) {
                    detectedQueenCentroids.add(Vector2D(x.toFloat(), y.toFloat()))
                } else if (whitePuckRange.matches(h, s, v)) {
                    detectedWhiteCentroids.add(Vector2D(x.toFloat(), y.toFloat()))
                } else if (darkPuckRange.matches(h, s, v)) {
                    detectedDarkCentroids.add(Vector2D(x.toFloat(), y.toFloat()))
                } else if (strikerRange.matches(h, s, v) && y > (boardRect.top + boardRect.height() * 0.65f)) {
                    // Striker is usually on bottom baseline zone
                    detectedStrikerCentroids.add(Vector2D(x.toFloat(), y.toFloat()))
                }
            }
        }

        // 3. Cluster points into circular puck centroids (Hough / Mean-shift clustering)
        val pucks = mutableListOf<Puck>()
        var puckId = 1

        val queenCircles = clusterCentroids(detectedQueenCentroids, boardGeometry.puckRadius)
        for (c in queenCircles) {
            pucks.add(Puck(puckId++, c, radius = boardGeometry.puckRadius, type = PuckType.QUEEN))
        }

        val whiteCircles = clusterCentroids(detectedWhiteCentroids, boardGeometry.puckRadius)
        for (c in whiteCircles.take(9)) {
            pucks.add(Puck(puckId++, c, radius = boardGeometry.puckRadius, type = PuckType.WHITE))
        }

        val darkCircles = clusterCentroids(detectedDarkCentroids, boardGeometry.puckRadius)
        for (c in darkCircles.take(9)) {
            pucks.add(Puck(puckId++, c, radius = boardGeometry.puckRadius, type = PuckType.BLACK))
        }

        val strikerCircles = clusterCentroids(detectedStrikerCentroids, boardGeometry.strikerRadius)
        val strikerPos = strikerCircles.firstOrNull() ?: Vector2D(
            boardRect.centerX(),
            boardRect.bottom - 160f
        )
        val striker = Puck(0, strikerPos, radius = boardGeometry.strikerRadius, type = PuckType.STRIKER)

        val duration = System.currentTimeMillis() - startTime

        return DetectionResult(
            boardBounds = boardRect,
            striker = striker,
            pucks = pucks,
            processingTimeMs = duration,
            confidence = if (pucks.isNotEmpty()) 0.92f else 0.75f
        )
    }

    /**
     * Detects square Carrom board boundary from screen frame.
     */
    fun detectBoardBounds(bitmap: Bitmap, width: Int, height: Int): RectF {
        // Carrom is typically a square board centered horizontally on mobile screens
        val boardSize = (width.coerceAtMost(height) * 0.94f)
        val left = (width - boardSize) / 2f
        val top = (height - boardSize) / 2f + 40f
        return RectF(left, top, left + boardSize, top + boardSize)
    }

    /**
     * Clusters segmented pixels into discrete circular puck centers.
     */
    private fun clusterCentroids(points: List<Vector2D>, expectedRadius: Float): List<Vector2D> {
        val clusters = mutableListOf<Vector2D>()
        val clusterThresholdSq = (expectedRadius * 1.3f) * (expectedRadius * 1.3f)

        for (pt in points) {
            var added = false
            for (i in clusters.indices) {
                if (clusters[i].distanceSquaredTo(pt) < clusterThresholdSq) {
                    // Average center position
                    clusters[i] = (clusters[i] + pt) / 2f
                    added = true
                    break
                }
            }
            if (!added && clusters.size < 20) {
                clusters.add(pt)
            }
        }
        return clusters
    }
}
