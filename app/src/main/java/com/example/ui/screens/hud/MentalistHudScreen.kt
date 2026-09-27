package com.example.ui.screens.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CreatureState
import com.example.domain.model.MagneticReading
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardBorder
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.SlumberGreen
import com.example.ui.theme.StirringAmber
import com.example.ui.theme.StrikeMagenta
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextHigh
import com.example.ui.theme.TextMedium
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun MentalistHudScreen(
    reading: MagneticReading,
    creatureState: CreatureState,
    effectiveThreshold: Float,
    onTareBaseline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // History buffer for oscilloscope graph (approx 60 points)
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(reading.deltaMagnitude) {
        history.add(reading.deltaMagnitude)
        if (history.size > 80) {
            history.removeAt(0)
        }
    }

    val stateColor = when (creatureState) {
        CreatureState.SLUMBERING -> SlumberGreen
        CreatureState.STIRRING -> StirringAmber
        CreatureState.AWAKE -> ElectricCyan
        CreatureState.STRIKING -> StrikeMagenta
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Oscilloscope Header & Real-time Graph
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Scope",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "MAGNETIC DELTA SCOPE",
                            color = TextHigh,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = String.format(Locale.US, "Δ %.2f µT", reading.deltaMagnitude),
                        color = stateColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Waveform Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(MidnightSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, MidnightCardBorder, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val maxDisplayVal = max(effectiveThreshold * 1.6f, 10f)

                        // Draw Grid lines
                        val gridCount = 4
                        for (g in 1..gridCount) {
                            val y = h * (g.toFloat() / (gridCount + 1))
                            drawLine(
                                color = MidnightCardBorder.copy(alpha = 0.5f),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1f
                            )
                        }

                        // Draw Fog Threshold Line
                        val threshY = h - (effectiveThreshold / maxDisplayVal).coerceIn(0f, 1f) * h
                        drawLine(
                            color = ElectricCyan.copy(alpha = 0.8f),
                            start = Offset(0f, threshY),
                            end = Offset(w, threshY),
                            strokeWidth = 2f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )

                        // Draw Noise Floor Line
                        val noiseY = h - (reading.noiseFloor / maxDisplayVal).coerceIn(0f, 1f) * h
                        drawLine(
                            color = TextDim.copy(alpha = 0.7f),
                            start = Offset(0f, noiseY),
                            end = Offset(w, noiseY),
                            strokeWidth = 1.5f
                        )

                        // Draw Delta History Path
                        if (history.size >= 2) {
                            val path = Path()
                            val stepX = w / (history.size - 1)

                            for (i in history.indices) {
                                val vx = i * stepX
                                val vy = h - (history[i] / maxDisplayVal).coerceIn(0f, 1f) * h
                                if (i == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
                            }

                            drawPath(
                                path = path,
                                color = stateColor,
                                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(Locale.US, "Noise Floor: %.2f µT", reading.noiseFloor),
                        color = TextDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "Threshold: %.1f µT", effectiveThreshold),
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 2. 3D Spatial Vector & Approach Direction
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "APPROACH VECTOR (DIRECTION)",
                    color = TextHigh,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )

                // Axis breakout bars
                AxisBar(name = "X Axis (Left / Right)", delta = reading.deltaX, color = ElectricCyan)
                AxisBar(name = "Y Axis (Top / Bottom)", delta = reading.deltaY, color = SlumberGreen)
                AxisBar(name = "Z Axis (Screen / Back)", delta = reading.deltaZ, color = StirringAmber)

                // Dominant Approach Summary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSurface, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "DOMINANT PASS: ${reading.dominantDirection.label.uppercase()}",
                            color = stateColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = reading.dominantDirection.hint,
                            color = TextMedium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 3. Technical Telemetry Readout Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "RAW FLUX TELEMETRY",
                    color = TextMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryItem(label = "Total Field", value = String.format(Locale.US, "%.1f µT", reading.totalFieldMagnitude))
                    TelemetryItem(label = "Rate dB/dt", value = String.format(Locale.US, "%.1f µT/s", reading.rateOfChange))
                    TelemetryItem(label = "Noise Floor", value = String.format(Locale.US, "%.2f µT", reading.noiseFloor))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryItem(label = "Base X", value = String.format(Locale.US, "%.1f", reading.baselineX))
                    TelemetryItem(label = "Base Y", value = String.format(Locale.US, "%.1f", reading.baselineY))
                    TelemetryItem(label = "Base Z", value = String.format(Locale.US, "%.1f", reading.baselineZ))
                }
            }
        }

        // 4. Instant Tare Baseline Button
        Button(
            onClick = onTareBaseline,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("hud_tare_baseline_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SlumberGreen.copy(alpha = 0.2f),
                contentColor = SlumberGreen
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlumberGreen.copy(alpha = 0.6f))
        ) {
            Icon(
                imageVector = Icons.Default.CompassCalibration,
                contentDescription = "Tare Baseline",
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Recalibrate Baseline Now",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun AxisBar(name: String, delta: Float, color: Color) {
    val absVal = abs(delta)
    val maxScale = 10f
    val fraction = (absVal / maxScale).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, color = TextMedium, fontSize = 12.sp)
            Text(
                text = String.format(Locale.US, "%+.2f µT", delta),
                color = color,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = MidnightCardBorder
        )
    }
}

@Composable
fun TelemetryItem(label: String, value: String) {
    Column {
        Text(text = label, color = TextDim, fontSize = 10.sp)
        Text(
            text = value,
            color = TextHigh,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}
