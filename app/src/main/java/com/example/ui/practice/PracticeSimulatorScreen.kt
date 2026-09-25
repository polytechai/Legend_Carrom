package com.example.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BoardGeometry
import com.example.model.MistakeAnalysis
import com.example.model.OverlayConfig
import com.example.model.Puck
import com.example.model.PuckType
import com.example.model.TrajectoryNodeType
import com.example.model.Vector2D
import com.example.physics.BoardPhysicsSimulator
import com.example.physics.PhysicsCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Standalone 2D Practice Simulator (Offline Mode).
 * Features:
 * - Interactive draggable Carrom board with Striker & Pucks
 * - Real-time continuous trajectory line prediction directly on the board
 * - Cushion bounce reflection lines & multi-ball chain trajectories
 * - Interactive Angle & Power gauge
 * - Physical "Strike" simulation with real elastic collisions & potting
 * - Mistake Analysis Engine feedback after each shot
 */
@Composable
fun PracticeSimulatorScreen(
    initialPucks: List<Puck>? = null,
    initialStrikerPos: Vector2D? = null,
    targetAngle: Float? = null,
    targetPower: Int? = null,
    onNavigateToTrickShots: () -> Unit = {}
) {
    val board = remember { BoardGeometry(left = 0f, top = 0f, right = 1000f, bottom = 1000f) }
    val physicsCalculator = remember { PhysicsCalculator(board) }
    val physicsSimulator = remember { BoardPhysicsSimulator(board) }

    var strikerPos by remember {
        mutableStateOf(initialStrikerPos ?: Vector2D(board.centerX, board.bottomBaselineY))
    }
    var aimAngle by remember { mutableFloatStateOf(targetAngle ?: 65f) }
    var shotPower by remember { mutableIntStateOf(targetPower ?: 70) }
    var isSimulating by remember { mutableStateOf(false) }

    val pucks = remember {
        mutableStateListOf<Puck>().apply {
            if (!initialPucks.isNullOrEmpty()) {
                addAll(initialPucks)
            } else {
                addAll(createDefaultRack(board))
            }
        }
    }

    var strikerPuck by remember {
        mutableStateOf(Puck(0, strikerPos, radius = board.strikerRadius, type = PuckType.STRIKER))
    }

    var mistakeAnalysis by remember { mutableStateOf<MistakeAnalysis?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Trajectory calculation for preview lines
    val trajectoryResult = remember(strikerPos, aimAngle, pucks.toList(), isSimulating) {
        if (isSimulating) null else {
            val aimDir = Vector2D.fromAngle(aimAngle)
            physicsCalculator.calculateTrajectory(
                strikerPos = strikerPos,
                aimDirection = aimDir,
                pucks = pucks.toList(),
                maxBounces = 2,
                allowSecondaryCollision = true
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "2D Practice Simulator",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Drag striker along baseline or drag board to aim",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            OutlinedButton(
                onClick = onNavigateToTrickShots,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("trick_shots_btn")
            ) {
                Icon(Icons.Default.Sports, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Trick Shots", color = Color(0xFF00E5FF), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Interactive Carrom Board Canvas
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
        ) {
            val canvasWidthPx = constraints.maxWidth.toFloat()
            val canvasHeightPx = constraints.maxHeight.toFloat()
            val scale = canvasWidthPx / 1000f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isSimulating) {
                        if (isSimulating) return@pointerInput
                        detectDragGestures { change, _ ->
                            change.consume()
                            val touchX = change.position.x / scale
                            val touchY = change.position.y / scale

                            // If touch is near baseline, move striker
                            if (touchY > board.bottomBaselineY - 40f && touchY < board.bottomBaselineY + 80f) {
                                val clampedX = touchX.coerceIn(board.baselineLeftX, board.baselineRightX)
                                strikerPos = Vector2D(clampedX, board.bottomBaselineY)
                                strikerPuck = strikerPuck.copy(position = strikerPos)
                            } else {
                                // Aim towards touch point
                                val delta = Vector2D(touchX, touchY) - strikerPos
                                if (delta.lengthSquared() > 400f) {
                                    val deg = Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())).toFloat()
                                    aimAngle = if (deg < 0) deg + 360f else deg
                                }
                            }
                        }
                    }
                    .testTag("carrom_board_canvas")
            ) {
                // 1. Draw Wooden Frame & Felt Board
                drawCarromBoardBackground(board, scale)

                // 2. Draw Trajectory Lines & Cushion Rebounds (when not simulating shot)
                if (!isSimulating && trajectoryResult != null) {
                    drawTrajectoryLines(trajectoryResult, scale)
                }

                // 3. Draw Active Pucks
                for (puck in pucks) {
                    if (!puck.isPotted) {
                        drawPuck(puck, scale)
                    }
                }

                // 4. Draw Striker
                if (!strikerPuck.isPotted) {
                    drawStriker(strikerPuck, scale, aimAngle)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Angle & Power Meter Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aim & Power Guide", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }

                    trajectoryResult?.alignedPocket?.let { pocket ->
                        Text(
                            text = "Target: ${pocket.name} (${trajectoryResult.pocketConfidence.toInt()}%)",
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Angle Slider / Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Aim Angle: ${"%.1f".format(aimAngle)}°", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    trajectoryResult?.let {
                        Text("Cut: ${"%.1f".format(it.cutAngleDegrees)}°", color = Color(0xFFFFD600), fontSize = 13.sp)
                    }
                }
                Slider(
                    value = aimAngle,
                    onValueChange = { aimAngle = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.testTag("angle_slider")
                )

                // Power Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Shot Power: $shotPower%", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    trajectoryResult?.let {
                        Text("Rec Power: ${it.recommendedPower}%", color = Color(0xFF00E676), fontSize = 13.sp)
                    }
                }
                Slider(
                    value = shotPower.toFloat(),
                    onValueChange = { shotPower = it.toInt() },
                    valueRange = 30f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E676),
                        activeTrackColor = Color(0xFF00E676),
                        inactiveTrackColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.testTag("power_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons: Strike, Reset, Rack
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (!isSimulating) {
                        isSimulating = true
                        mistakeAnalysis = null

                        // Launch striker with velocity along aim direction
                        val aimDir = Vector2D.fromAngle(aimAngle)
                        val speed = (shotPower.toFloat() / 100f) * 32f
                        strikerPuck = strikerPuck.copy(
                            velocity = aimDir * speed,
                            isPotted = false
                        )

                        coroutineScope.launch {
                            var moving = true
                            var steps = 0
                            while (moving && isActive && steps < 250) {
                                moving = physicsSimulator.step(pucks, strikerPuck)
                                steps++
                                delay(16)
                            }
                            isSimulating = false

                            // Shot outcome analysis
                            val targetPocket = trajectoryResult?.alignedPocket
                            val idealAngle = targetAngle ?: trajectoryResult?.shotAngleDegrees ?: aimAngle
                            val idealPower = targetPower ?: trajectoryResult?.recommendedPower ?: 70
                            val anyPuckPotted = pucks.any { it.isPotted }

                            mistakeAnalysis = MistakeAnalysis.evaluate(
                                idealAngle = idealAngle,
                                actualAngle = aimAngle,
                                idealPower = idealPower,
                                actualPower = shotPower,
                                potted = anyPuckPotted,
                                targetPocket = targetPocket
                            )
                        }
                    }
                },
                enabled = !isSimulating,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("strike_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0F172A))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSimulating) "Simulating..." else "STRIKE",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            OutlinedButton(
                onClick = {
                    isSimulating = false
                    physicsSimulator.resetStriker(strikerPuck, board.centerX)
                    strikerPos = strikerPuck.position
                    mistakeAnalysis = null
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("reset_striker_btn")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Striker", color = Color.White)
            }

            OutlinedButton(
                onClick = {
                    isSimulating = false
                    pucks.clear()
                    pucks.addAll(createDefaultRack(board))
                    physicsSimulator.resetStriker(strikerPuck, board.centerX)
                    strikerPos = strikerPuck.position
                    mistakeAnalysis = null
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("rack_center_btn")
            ) {
                Text("Re-Rack", color = Color(0xFFFFD600))
            }
        }

        // Mistake Analysis Feedback Card (Appears after shot)
        AnimatedVisibility(
            visible = mistakeAnalysis != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            mistakeAnalysis?.let { analysis ->
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_analysis_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (analysis.wasPotted) Color(0xFF064E3B) else Color(0xFF450A0A)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Analytics, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Shot Analysis (${analysis.accuracyScore}% Accuracy)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = analysis.feedbackTitle,
                            color = if (analysis.wasPotted) Color(0xFF34D399) else Color(0xFFF87171),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = analysis.feedbackDetails,
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Coach Tip: ${analysis.suggestion}",
                            color = Color(0xFFFFD600),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// Drawing helper functions for the Carrom board Canvas
private fun DrawScope.drawCarromBoardBackground(board: BoardGeometry, scale: Float) {
    val bLeft = board.left * scale
    val bTop = board.top * scale
    val bWidth = board.width * scale
    val bHeight = board.height * scale

    // Wooden Border
    drawRect(
        brush = Brush.linearGradient(listOf(Color(0xFF5D4037), Color(0xFF3E2723))),
        topLeft = Offset(bLeft, bTop),
        size = Size(bWidth, bHeight)
    )

    // Playing Surface (Ivory/Natural Wood tone)
    val pLeft = board.playLeft * scale
    val pTop = board.playTop * scale
    val pWidth = (board.playRight - board.playLeft) * scale
    val pHeight = (board.playBottom - board.playTop) * scale

    drawRect(
        color = Color(0xFFF5ECD7),
        topLeft = Offset(pLeft, pTop),
        size = Size(pWidth, pHeight)
    )

    // Cushions
    val cushionStroke = board.cushionInset * scale
    drawRect(
        color = Color(0xFF42210B),
        topLeft = Offset(bLeft, bTop),
        size = Size(bWidth, bHeight),
        style = Stroke(width = cushionStroke)
    )

    // Center Circles
    val cx = board.centerX * scale
    val cy = board.centerY * scale
    drawCircle(color = Color(0xFFD32F2F), radius = 24f * scale, center = Offset(cx, cy))
    drawCircle(color = Color(0xFF1E293B), radius = 90f * scale, center = Offset(cx, cy), style = Stroke(2f * scale))
    drawCircle(color = Color(0xFF1E293B), radius = 100f * scale, center = Offset(cx, cy), style = Stroke(1.5f * scale))

    // Player Bottom Baseline
    val baselineY = board.bottomBaselineY * scale
    val blLeft = board.baselineLeftX * scale
    val blRight = board.baselineRightX * scale
    drawLine(
        color = Color(0xFF8D6E63),
        start = Offset(blLeft, baselineY - 14f * scale),
        end = Offset(blRight, baselineY - 14f * scale),
        strokeWidth = 2f * scale
    )
    drawLine(
        color = Color(0xFF8D6E63),
        start = Offset(blLeft, baselineY + 14f * scale),
        end = Offset(blRight, baselineY + 14f * scale),
        strokeWidth = 2f * scale
    )
    // Baseline end circles
    drawCircle(color = Color(0xFFD32F2F), radius = 14f * scale, center = Offset(blLeft, baselineY))
    drawCircle(color = Color(0xFFD32F2F), radius = 14f * scale, center = Offset(blRight, baselineY))

    // 4 Corner Pockets
    for (pocket in board.pockets) {
        val px = pocket.position.x * scale
        val py = pocket.position.y * scale
        val pr = pocket.radius * scale
        drawCircle(color = Color(0xFF0F172A), radius = pr, center = Offset(px, py))
        drawCircle(color = Color(0xFF64748B), radius = pr + 2f * scale, center = Offset(px, py), style = Stroke(2f * scale))
    }
}

private fun DrawScope.drawTrajectoryLines(
    result: com.example.model.TrajectoryResult,
    scale: Float
) {
    // 1. Striker Line
    for (seg in result.strikerPath) {
        drawLine(
            color = if (seg.isCushionBounce) Color(0xFFFF4081) else Color(0xFF00E5FF),
            start = Offset(seg.start.x * scale, seg.start.y * scale),
            end = Offset(seg.end.x * scale, seg.end.y * scale),
            strokeWidth = 4f * scale,
            cap = StrokeCap.Round,
            pathEffect = if (seg.isCushionBounce) PathEffect.dashPathEffect(floatArrayOf(16f, 8f)) else null
        )
    }

    // 2. Target Puck Line
    for (seg in result.targetPuckPath) {
        drawLine(
            color = if (seg.isCushionBounce) Color(0xFFFF4081) else Color(0xFFFFD600),
            start = Offset(seg.start.x * scale, seg.start.y * scale),
            end = Offset(seg.end.x * scale, seg.end.y * scale),
            strokeWidth = 4f * scale,
            cap = StrokeCap.Round,
            pathEffect = if (seg.isCushionBounce) PathEffect.dashPathEffect(floatArrayOf(16f, 8f)) else null
        )
    }

    // 3. Secondary Combo Line
    for (seg in result.secondaryPuckPath) {
        drawLine(
            color = Color(0xFF00E676),
            start = Offset(seg.start.x * scale, seg.start.y * scale),
            end = Offset(seg.end.x * scale, seg.end.y * scale),
            strokeWidth = 3.5f * scale,
            cap = StrokeCap.Round
        )
    }

    // 4. Ghost Contact Ball
    result.contactGhostPuckPos?.let { ghost ->
        val gx = ghost.x * scale
        val gy = ghost.y * scale
        val gr = 34f * scale
        drawCircle(color = Color(0x3300E5FF), radius = gr, center = Offset(gx, gy))
        drawCircle(color = Color(0xCCFFFFFF), radius = gr, center = Offset(gx, gy), style = Stroke(2f * scale))
    }

    // 5. Nodes
    for (node in result.nodes) {
        val nx = node.position.x * scale
        val ny = node.position.y * scale
        when (node.type) {
            TrajectoryNodeType.CUSHION_BOUNCE -> {
                drawCircle(color = Color(0xFFFF4081), radius = 8f * scale, center = Offset(nx, ny))
                drawCircle(color = Color.White, radius = 12f * scale, center = Offset(nx, ny), style = Stroke(2f * scale))
            }
            TrajectoryNodeType.POCKET_DESTINATION -> {
                drawCircle(color = Color(0xFF00E676), radius = 16f * scale, center = Offset(nx, ny))
                drawCircle(color = Color.White, radius = 24f * scale, center = Offset(nx, ny), style = Stroke(2.5f * scale))
            }
            else -> {}
        }
    }
}

private fun DrawScope.drawPuck(puck: Puck, scale: Float) {
    val px = puck.position.x * scale
    val py = puck.position.y * scale
    val pr = puck.radius * scale

    val fillColor = when (puck.type) {
        PuckType.WHITE -> Color(0xFFF1F5F9)
        PuckType.BLACK -> Color(0xFF1E293B)
        PuckType.QUEEN -> Color(0xFFDC2626)
        PuckType.STRIKER -> Color(0xFFE2E8F0)
    }
    val ringColor = when (puck.type) {
        PuckType.WHITE -> Color(0xFF94A3B8)
        PuckType.BLACK -> Color(0xFF475569)
        PuckType.QUEEN -> Color(0xFFFBBF24)
        PuckType.STRIKER -> Color(0xFF00E5FF)
    }

    drawCircle(color = fillColor, radius = pr, center = Offset(px, py))
    drawCircle(color = ringColor, radius = pr, center = Offset(px, py), style = Stroke(2.5f * scale))
    drawCircle(color = ringColor, radius = pr * 0.45f, center = Offset(px, py), style = Stroke(1.5f * scale))
}

private fun DrawScope.drawStriker(striker: Puck, scale: Float, aimAngle: Float) {
    val sx = striker.position.x * scale
    val sy = striker.position.y * scale
    val sr = striker.radius * scale

    // Striker disc
    drawCircle(color = Color(0xFFE0F2FE), radius = sr, center = Offset(sx, sy))
    drawCircle(color = Color(0xFF00E5FF), radius = sr, center = Offset(sx, sy), style = Stroke(3f * scale))
    drawCircle(color = Color(0xFF0284C7), radius = sr * 0.5f, center = Offset(sx, sy), style = Stroke(2f * scale))

    // Aim direction pointer
    val rad = Math.toRadians(aimAngle.toDouble())
    val dirX = (sx + kotlin.math.cos(rad) * (sr + 16f * scale)).toFloat()
    val dirY = (sy + kotlin.math.sin(rad) * (sr + 16f * scale)).toFloat()
    drawLine(
        color = Color(0xFF00E5FF),
        start = Offset(sx, sy),
        end = Offset(dirX, dirY),
        strokeWidth = 3f * scale,
        cap = StrokeCap.Round
    )
}

private fun createDefaultRack(board: BoardGeometry): List<Puck> {
    val list = mutableListOf<Puck>()
    val cx = board.centerX
    val cy = board.centerY

    // Queen at center
    list.add(Puck(1, Vector2D(cx, cy), type = PuckType.QUEEN))

    // Inner ring (alternating 3 white, 3 black)
    val r1 = 52f
    for (i in 0 until 6) {
        val angle = i * 60f
        val rad = Math.toRadians(angle.toDouble())
        val x = (cx + kotlin.math.cos(rad) * r1).toFloat()
        val y = (cy + kotlin.math.sin(rad) * r1).toFloat()
        list.add(Puck(i + 2, Vector2D(x, y), type = if (i % 2 == 0) PuckType.WHITE else PuckType.BLACK))
    }

    return list
}
