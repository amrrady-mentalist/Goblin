package com.example.ui.screens.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VenueProfileEntity
import com.example.domain.model.HapticFeedbackType
import com.example.domain.model.VibrationStrength
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardBorder
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.SlumberGreen
import com.example.ui.theme.StrikeMagenta
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextHigh
import com.example.ui.theme.TextMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    venueProfiles: List<VenueProfileEntity>,
    activeProfile: VenueProfileEntity?,
    currentHapticType: HapticFeedbackType,
    currentStrength: VibrationStrength,
    autoPocketStealth: Boolean,
    volumeKeyTare: Boolean,
    stealthMicroDot: Boolean,
    onSelectProfile: (VenueProfileEntity) -> Unit,
    onSaveProfile: (name: String, desc: String) -> Unit,
    onDeleteProfile: (VenueProfileEntity) -> Unit,
    onSelectHaptic: (HapticFeedbackType) -> Unit,
    onSelectStrength: (VibrationStrength) -> Unit,
    onToggleAutoPocket: (Boolean) -> Unit,
    onToggleVolumeTare: (Boolean) -> Unit,
    onToggleMicroDot: (Boolean) -> Unit,
    onTestHaptic: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSaveDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var newProfileDesc by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightSurface,
        scrimColor = MidnightBg.copy(alpha = 0.75f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
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
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = ElectricCyan
                    )
                    Text(
                        text = "GOBLIN CONFIGURATION",
                        color = TextHigh,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMedium
                    )
                }
            }

            // Section 1: Venue Presets & Profiles
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PERFORMANCE VENUE PRESETS",
                        color = TextMedium,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    TextButton(onClick = { showSaveDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Save Custom Preset",
                            modifier = Modifier.size(16.dp),
                            tint = ElectricCyan
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = "Save Preset", color = ElectricCyan, fontSize = 12.sp)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    venueProfiles.forEach { profile ->
                        val isSelected = activeProfile?.id == profile.id || (activeProfile == null && profile.isBuiltIn && profile.name.startsWith("PK Magnetic"))
                        ProfileCard(
                            profile = profile,
                            isSelected = isSelected,
                            onSelect = { onSelectProfile(profile) },
                            onDelete = if (!profile.isBuiltIn) { { onDeleteProfile(profile) } } else null
                        )
                    }
                }
            }

            HorizontalDivider(color = MidnightCardBorder)

            // Section 2: Tactile Cue Engine
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TACTILE CUE PATTERN",
                        color = TextMedium,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    OutlinedButton(
                        onClick = onTestHaptic,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Test Cue",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = "Test Feel", fontSize = 11.sp)
                    }
                }

                HapticFeedbackType.values().forEach { type ->
                    val selected = currentHapticType == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MidnightCard else MidnightSurface)
                            .border(
                                1.dp,
                                if (selected) ElectricCyan.copy(alpha = 0.6f) else MidnightCardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectHaptic(type) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Default.Check else Icons.Default.Vibration,
                            contentDescription = type.displayName,
                            tint = if (selected) ElectricCyan else TextDim,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = type.displayName,
                                color = if (selected) TextHigh else TextMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = type.description,
                                color = TextDim,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Vibration Strength Chips
                Text(
                    text = "VIBRATION INTENSITY",
                    color = TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VibrationStrength.values().forEach { str ->
                        FilterChip(
                            selected = currentStrength == str,
                            onClick = { onSelectStrength(str) },
                            label = { Text(text = str.label.substringBefore(" "), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = MidnightCardBorder)

            // Section 3: Hands-Free & Pocket Performance
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "HANDS-FREE & POCKET BEHAVIOR",
                    color = TextMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                SettingSwitchRow(
                    title = "Auto Pocket Stealth",
                    description = "Engages true black screen when phone is slipped in pocket or turned face-down.",
                    checked = autoPocketStealth,
                    onCheckedChange = onToggleAutoPocket
                )

                SettingSwitchRow(
                    title = "Volume Key Baseline Tare",
                    description = "Press Volume rocker inside pocket to silently recalibrate room background.",
                    checked = volumeKeyTare,
                    onCheckedChange = onToggleVolumeTare
                )

                SettingSwitchRow(
                    title = "Covert OLED Micro-Dot",
                    description = "Displays a microscopic 2px glance dot in screen corner during Stealth Mode.",
                    checked = stealthMicroDot,
                    onCheckedChange = onToggleMicroDot
                )
            }

            HorizontalDivider(color = MidnightCardBorder)

            // Section 4: Performer & Mentalist Field Guide
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Field Guide",
                            tint = SlumberGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Mentalism & Magic Field Guide",
                            color = TextHigh,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "• Motion vs Stasis: A magnet sitting still produces little response. Movement creates change, and change is what triggers the Goblin.\n" +
                               "• Pocket Placement: Breast pocket gives maximum sensitivity to hands moving near your chest. Trouser pocket detects magnetic coins or rings passed by your side.\n" +
                               "• Room Baseline: When entering a new venue (theatre, bar), tap 'Tare Room' or press Volume down to let the creature learn the local background field.",
                        color = TextMedium,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = MidnightSurface,
            title = {
                Text(text = "Save Venue Profile", color = TextHigh, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile / Venue Name") },
                        placeholder = { Text("e.g. Grand Theatre Stage") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = MidnightCardBorder,
                            focusedTextColor = TextHigh,
                            unfocusedTextColor = TextHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newProfileDesc,
                        onValueChange = { newProfileDesc = it },
                        label = { Text("Description (Optional)") },
                        placeholder = { Text("e.g. Ring routine with heavy lighting") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = MidnightCardBorder,
                            focusedTextColor = TextHigh,
                            unfocusedTextColor = TextHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            onSaveProfile(newProfileName, newProfileDesc)
                            showSaveDialog = false
                            newProfileName = ""
                            newProfileDesc = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightBg)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = TextMedium)
                }
            }
        )
    }
}

@Composable
fun ProfileCard(
    profile: VenueProfileEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MidnightCard else MidnightSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) ElectricCyan.copy(alpha = 0.6f) else MidnightCardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = profile.name,
                        color = if (isSelected) ElectricCyan else TextHigh,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (profile.isBuiltIn) {
                        Text(
                            text = "PRESET",
                            color = TextDim,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = profile.description,
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Profile",
                        tint = StrikeMagenta.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = TextHigh, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = description, color = TextDim, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MidnightBg,
                checkedTrackColor = ElectricCyan,
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = MidnightCard
            )
        )
    }
}
