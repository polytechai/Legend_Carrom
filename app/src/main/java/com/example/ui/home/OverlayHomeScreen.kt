package com.example.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OverlayHomeScreen(
    isOverlayPermissionGranted: Boolean,
    isServiceRunning: Boolean,
    onStartOverlayClick: () -> Unit,
    onStopOverlayClick: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onNavigateToSimulator: () -> Unit,
    onNavigateToTrickShots: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Carrom Aim Guide",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Physics Trajectory & Aim Assistant",
                    fontSize = 13.sp,
                    color = Color(0xFF00E5FF)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isServiceRunning) Color(0xFF064E3B) else Color(0xFF1E293B))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isServiceRunning) Color(0xFF00E676) else Color(0xFF94A3B8))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "ACTIVE" else "IDLE",
                        color = if (isServiceRunning) Color(0xFF00E676) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isOverlayPermissionGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Display Over Other Apps Required",
                            color = Color(0xFFFDE68A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Enable permission to draw trajectory lines directly over Carrom Disc Pool.",
                            color = Color(0xFFFEF3C7),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Allow", color = Color(0xFF451A03), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Floating Guideline Overlay",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Runs continuously as a floating window. Detects board elements and renders real-time 2D rebound lines, angle calculations, and multi-ball chain trajectories directly over your game.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!isServiceRunning) {
                    Button(
                        onClick = onStartOverlayClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_overlay_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0F172A))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "START OVERLAY SERVICE",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onStopOverlayClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("stop_overlay_btn")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STOP OVERLAY SERVICE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Training & Practice Modules",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                onClick = onNavigateToSimulator,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_simulator_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Sports, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("2D Practice Board", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Draggable offline board with live physics engine", color = Color(0xFF94A3B8), fontSize = 11.sp, lineHeight = 15.sp)
                }
            }

            Card(
                onClick = onNavigateToTrickShots,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_trick_shots_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Trick Shot Academy", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Master bank shots, cross cuts & combo chain shots", color = Color(0xFF94A3B8), fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Core Engine Features",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                FeatureItem(
                    title = "Continuous 2D Trajectory Ray",
                    desc = "Computes direct line from Striker to target Puck extending directly into pockets."
                )
                FeatureItem(
                    title = "Cushion & Rebound Reflections",
                    desc = "Calculates exact angle of incidence = angle of reflection on side rails."
                )
                FeatureItem(
                    title = "Multi-Ball Chain Collisions",
                    desc = "Simulates combo shots when target puck strikes another puck."
                )
                FeatureItem(
                    title = "Ghost Contact Ball & Pocket HUD",
                    desc = "Shows ghost circle marker at impact point with angle & power percentage."
                )
                FeatureItem(
                    title = "Movable In-Game Control Pill",
                    desc = "Easily toggle lines on/off without leaving your game."
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureItem(title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, color = Color(0xFFE2E8F0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = desc, color = Color(0xFF94A3B8), fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
