package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OverlayConfig

@Composable
fun OverlaySettingsScreen(
    config: OverlayConfig,
    onConfigChange: (OverlayConfig) -> Unit
) {
    val colorPresets = listOf(
        Color(0xFF00E5FF),
        Color(0xFFFFD600),
        Color(0xFFFF4081),
        Color(0xFF00E676),
        Color(0xFFFFFFFF),
        Color(0xFFE040FB)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Guideline Customization",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Configure trajectory line appearance and physics features",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
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
                    text = "Line Thickness (${"%.1f".format(config.strokeWidthDp)} dp)",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = config.strokeWidthDp,
                    onValueChange = { onConfigChange(config.copy(strokeWidthDp = it)) },
                    valueRange = 2f..10f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.testTag("stroke_width_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Guideline Colors",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Striker Ray Color", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                ColorPickerRow(
                    colors = colorPresets,
                    selectedColorArgb = config.strikerLineColor,
                    onSelect = { onConfigChange(config.copy(strikerLineColor = it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Target Puck Ray Color", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                ColorPickerRow(
                    colors = colorPresets,
                    selectedColorArgb = config.puckLineColor,
                    onSelect = { onConfigChange(config.copy(puckLineColor = it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Cushion Rebound Ray Color", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                ColorPickerRow(
                    colors = colorPresets,
                    selectedColorArgb = config.cushionLineColor,
                    onSelect = { onConfigChange(config.copy(cushionLineColor = it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Secondary Combo Ray Color", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                ColorPickerRow(
                    colors = colorPresets,
                    selectedColorArgb = config.secondaryLineColor,
                    onSelect = { onConfigChange(config.copy(secondaryLineColor = it)) }
                )
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
                    text = "Feature Toggles",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                ToggleRow(
                    title = "Cushion & Rebound Path",
                    description = "Predict 2D reflection angles when balls hit board edges",
                    checked = config.showCushionBounces,
                    onCheckedChange = { onConfigChange(config.copy(showCushionBounces = it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ToggleRow(
                    title = "Multi-Ball Collisions (Combos)",
                    description = "Calculate secondary branch lines when target puck strikes another puck",
                    checked = config.showSecondaryCollisions,
                    onCheckedChange = { onConfigChange(config.copy(showSecondaryCollisions = it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ToggleRow(
                    title = "Ghost Contact Ball",
                    description = "Draw circular ghost silhouette at the exact striker impact location",
                    checked = config.showGhostPuck,
                    onCheckedChange = { onConfigChange(config.copy(showGhostPuck = it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ToggleRow(
                    title = "Angle & Power HUD",
                    description = "Display floating badge with real-time degrees and recommended strike power",
                    checked = config.showAnglePowerMeter,
                    onCheckedChange = { onConfigChange(config.copy(showAnglePowerMeter = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ColorPickerRow(
    colors: List<Color>,
    selectedColorArgb: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (c in colors) {
            val isSelected = c.toArgb() == selectedColorArgb
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.White else Color(0x33FFFFFF),
                        shape = CircleShape
                    )
                    .clickable { onSelect(c.toArgb()) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (c == Color.White) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = description, color = Color(0xFF94A3B8), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0F172A),
                checkedTrackColor = Color(0xFF00E5FF),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}
