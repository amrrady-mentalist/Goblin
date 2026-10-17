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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Same frosted-glass language as the Settings screen, so switching tabs feels
// like one app rather than two different ones.
private val GradientTop = Color(0xFFEFE9FF)
private val GradientBottom = Color(0xFFE3F3FF)
private val ScreenGradient = Brush.verticalGradient(listOf(GradientTop, GradientBottom))
private val BlobLavender = Color(0xFFD6C6FF)
private val BlobSky = Color(0xFFBFE4FF)
private val TextTitleColor = Color(0xFF241B3D)
private val TextDescColor = Color(0xFF6B6480)
private val YellowAccent = Color(0xFFEAB308)
private val GlassCardBg = Color(0xFFFFFFFF).copy(alpha = 0.55f)
private val GlassCardBorder = Color(0xFFFFFFFF).copy(alpha = 0.65f)
private val GlassStroke = Color(0xFFFFFFFF).copy(alpha = 0.45f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenGradient)
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = (-70).dp, y = 420.dp)
                .blur(90.dp)
                .clip(CircleShape)
                .background(BlobLavender.copy(alpha = 0.5f))
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = 120.dp)
                .blur(90.dp)
                .clip(CircleShape)
                .background(BlobSky.copy(alpha = 0.5f))
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "Performance & Setup Guide",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Intro banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassCardBg)
                        .border(1.dp, GlassCardBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Performance Guide",
                        tint = YellowAccent,
                        modifier = Modifier.size(40.dp)
                    )
                    Column {
                        Text(
                            text = "How this works",
                            color = TextTitleColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "The phone's magnetometer learns the room, then you calibrate it to tonight's magnetic object before you go on.",
                            color = TextDescColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                TutorialStepItem(
                    icon = Icons.Default.Sensors,
                    title = "1. Sensor & Trick",
                    body = "\"Sensor\" turns the magnetometer on. The app spends about a second reading the room, with the phone kept still, and then shows \"Ready\". \"Trick\" arms detection once you are ready to perform."
                )

                TutorialStepItem(
                    icon = Icons.Default.Tune,
                    title = "2. Calibrate first",
                    body = "Tap Calibrate and follow the screen for 10 seconds. Step 1: hold the phone still with the object away. Step 2: move the phone gently, as if walking, with the object still away. Step 3: bring the object right up to the phone, then pull it away. The app then sets Territory, Magnetic Power and Phone Movement for you. You can adjust any slider by hand afterwards."
                )

                TutorialStepItem(
                    icon = Icons.Default.Speed,
                    title = "3. The four sliders",
                    body = "Territory: how far from the phone to listen, from close to far. Magnetic Power: how strong the object is, from an earbud to a strong neodymium magnet. Magnet Movement: how big a movement of the object should count, from a small shiver of the hand to a big swing. Phone Movement: how much movement of the phone itself is forgiven. Strict means the phone must be still; Relaxed means walking is fine. The line under the sliders tells you what they add up to."
                )

                TutorialStepItem(
                    icon = Icons.Default.CheckCircle,
                    title = "4. Up/Down only",
                    body = "Turn this on if the object always moves straight up or down, like a hand rising and falling. Sideways movement is then ignored. Calibration never changes this switch."
                )

                TutorialStepItem(
                    icon = Icons.Default.Vibration,
                    title = "5. Vibration",
                    body = "Choose Pulses, Buzz, Stutter, Tick, or Off (dot only). Pick how many vibrations (1 to 5), how hard, how fast and how long, and use Test to feel it. In a pocket, choose Hard strength and 2 or 3 pulses."
                )

                TutorialStepItem(
                    icon = Icons.Default.Alarm,
                    title = "6. Second cue",
                    body = "Strong objects only. When the object is strong enough, the app checks about a second after the first cue whether it is STILL close, and plays the second cue if it is. If the object only passed by, you get no second cue. Choose its pattern and count separately, or turn it Off."
                )

                TutorialStepItem(
                    icon = Icons.Default.Bedtime,
                    title = "7. Saturated sensor",
                    body = "If a magnet is pressed right against the phone, the sensor can overload. The app then pauses detection and shows a red message. Click either volume key, or just move the phone, to clear it."
                )

                TutorialStepItem(
                    icon = Icons.Default.CheckCircle,
                    title = "8. Screen Off & the dot",
                    body = "\"Turn Screen Off\" at the bottom makes the screen pitch black while detection keeps running. Swipe down with two fingers to come back. \"Show dot\" lights a tiny dot in the top-left corner on a detection: green for a strong hit, yellow for a weak one, and it stays on while a strong object stays close."
                )

                TutorialStepItem(
                    icon = Icons.Default.VolumeUp,
                    title = "9. Volume keys",
                    body = "Click either volume key at any time, even in your pocket, to re-centre the detector right where you are. A short tick confirms it. This also clears a saturated sensor."
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun TutorialStepItem(
    icon: ImageVector,
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassCardBg)
            .border(1.dp, GlassCardBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
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
