package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBg = Color(0xFFFAFBFB)
private val TextTitleColor = Color(0xFF263238)
private val TextDescColor = Color(0xFF78909C)
private val YellowAccent = Color(0xFFEAB308)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Video Tutorial & Guide",
                    color = TextTitleColor,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextTitleColor
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ScreenBg)
        )

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Video Tutorial Card banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = "Video Tutorial",
                        tint = YellowAccent,
                        modifier = Modifier.size(44.dp)
                    )
                    Column {
                        Text(
                            text = "Magician Performance Guide",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Learn how to configure Sensitivity and Sleeping Mode for walk-around and close-up magic.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Setting 1: Start Trick & Sensor
            TutorialStepItem(
                icon = Icons.Default.Sensors,
                title = "1. Start Trick & Activate Sensor",
                body = "Toggle 'Activate sensor' to turn on hardware magnetometer polling. Tap 'Start Trick' when you are ready to perform. The phone will secretly vibrate in your pocket when a hidden magnet passes by."
            )

            // Setting 2: Sensitivity (Stationary)
            TutorialStepItem(
                icon = Icons.Default.Tune,
                title = "2. Sensitivity (Stationary)",
                body = "Recommended default: 1.9.\nSwipe RIGHT to increase detection distance (up to 30-50 cm).\nSwipe LEFT to reduce false positives if resting on a metal table or near appliances."
            )

            // Setting 3: Adaptative Sensitivity (Moving)
            TutorialStepItem(
                icon = Icons.Default.Speed,
                title = "3. Adaptative Sensitivity (In Motion)",
                body = "Recommended default: 8.\nGoverns how the detector behaves while you walk around or move your hand. Swipe LEFT to increase freedom of movement and prevent vibrations from phone motion. Swipe RIGHT to increase reading distance while moving."
            )

            // Setting 4: Smart Alarm
            TutorialStepItem(
                icon = Icons.Default.Alarm,
                title = "4. Smart Alarm (Saturation Warning)",
                body = "Default value: 150 µT.\nIf the ambient magnetic field exceeds 150 µT (e.g. standing directly over a speaker or large transformer), the phone buzzes to warn you that the sensor is saturated and cannot detect trick passes. Move away to reset."
            )

            // Setting 5: Sleeping Mode
            TutorialStepItem(
                icon = Icons.Default.Bedtime,
                title = "5. Sleeping Mode (Covert Activation)",
                body = "Turn off 'Activate sensor' first, then switch on 'Sleeping Mode'. Put the phone in your pocket.\nTo start the trick covertly during your routine, wave your magnetic ring or prop against your pocket: the phone will buzz 3 times to confirm it is awake and active!"
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TutorialStepItem(
    icon: ImageVector,
    title: String,
    body: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = YellowAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    color = TextTitleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = body,
                color = TextDescColor,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
