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
                    title = "1. Activate Sensor & Start Trick",
                    body = "\"Activate sensor\" turns the magnetometer on. The app immediately spends about a second reading the room's own magnetic field \u2014 you'll see a \"Reading the surrounding magnetic field\u2026\" banner, followed by a brief \"Ready\" confirmation once it's done. \"Start Trick\" arms detection once you're ready to perform."
                )

                TutorialStepItem(
                    icon = Icons.Default.Tune,
                    title = "2. Detection Mode",
                    body = "Perimeter Proximity and the Earbud trick both react to a field change from any direction \u2014 the right choice for closing distance on a hand from whatever angle you're standing at. Up/Down Y-Axis only reacts to strict vertical motion and ignores everything else; use it only if the object moves straight up or down past a phone that's staying still."
                )

                TutorialStepItem(
                    icon = Icons.Default.Speed,
                    title = "3. Sensitivity & Adaptive Sensitivity",
                    body = "Sensitivity governs detection while the phone is resting still; Adaptive Sensitivity governs it while the phone is being carried or handled. Both get set automatically by Calibrate To Object below \u2014 you shouldn't usually need to hand-tune them unless you want to nudge the result afterward."
                )

                TutorialStepItem(
                    icon = Icons.Default.CheckCircle,
                    title = "4. Calibrate To Tonight's Object",
                    body = "Different objects need different settings \u2014 a hidden earbud magnet is far weaker than a fridge magnet or a watch charger at the same distance. Before each performance, move tonight's actual object through the real distance and angle you'll use live, tap Calibrate, and keep moving it for the full 3 seconds. Always calibrate at your FARTHEST expected distance, not your closest \u2014 a detector tuned for the hard case still catches the easy case; tuned the other way round, it can miss the hard case entirely."
                )

                TutorialStepItem(
                    icon = Icons.Default.Vibration,
                    title = "5. Two-Feel Vibration",
                    body = "A normal detection plays the \"Detected\" pattern. One that clears threshold by a wide margin \u2014 a strong magnet, or the object passing very close \u2014 automatically plays \"Strong/Close\" instead. The Strong/Close Cutoff slider controls how big that margin needs to be. The live reading dot follows the same idea: green while quiet, yellow for a faint reading, red once it's strong."
                )

                TutorialStepItem(
                    icon = Icons.Default.Alarm,
                    title = "6. Smart Alarm",
                    body = "An independent safety warning, separate from normal detection: if the raw field ever gets unusually strong (for example, standing right next to a speaker or large motor), the phone gives a distinct alert so you know a reading might not be trustworthy. Type a threshold directly into the box in \u00b5T."
                )

                TutorialStepItem(
                    icon = Icons.Default.Bedtime,
                    title = "7. Sleeping Mode",
                    body = "Turn off \"Activate sensor\" first, then switch on Sleeping Mode and pocket the phone. To covertly arm the trick mid-routine, bring your magnetic prop close to your pocket \u2014 the phone wakes itself and confirms with a distinct buzz."
                )

                TutorialStepItem(
                    icon = Icons.Default.CheckCircle,
                    title = "8. Screen Off Mode & Visual Dot",
                    body = "Screen Off Mode turns the display pitch black and locks it into an immersive, locked-looking state while detection keeps running underneath. Swipe down with two fingers anywhere on the black screen to return to the app. Visual Mode adds a small, deliberately faint dot in the top-left corner on a hit \u2014 colored the same green/yellow/red as the live reading \u2014 for cueing yourself without relying on vibration alone."
                )

                TutorialStepItem(
                    icon = Icons.Default.VolumeUp,
                    title = "9. Volume Key Baseline Tare (no switch \u2014 always on)",
                    body = "Press either volume button at any time, even with the phone in your pocket, to silently re-center the baseline right where you are. There's a quiet confirmation tick so you know it registered. Handy if you feel the baseline may have drifted mid-set and want a discreet reset without pulling the phone out."
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
