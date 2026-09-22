package com.loony.bodokeyboard.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loony.bodokeyboard.ui.theme.Accent
import com.loony.bodokeyboard.ui.theme.DivLine
import com.loony.bodokeyboard.ui.theme.ErrorRed
import com.loony.bodokeyboard.ui.theme.Surface1
import com.loony.bodokeyboard.ui.theme.Surface2
import com.loony.bodokeyboard.ui.theme.TextPri
import com.loony.bodokeyboard.ui.theme.TextSec
import com.loony.bodokeyboard.viewmodel.KeyboardViewModel

@Composable
fun SettingsView() {
    val context = LocalContext.current
    val vm: KeyboardViewModel = viewModel()
    val prefs = remember { context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }

    // "Clear learned words" needs the DB open; Settings can be opened without
    // ever visiting "Try It" first, which is the only other place that initializes it.
    LaunchedEffect(Unit) { vm.initDatabase(context) }

    // ── Preferences ───────────────────────────────────────────────────────────
    var haptic       by remember { mutableStateOf(prefs.getBoolean("haptic", true)) }
    var sound        by remember { mutableStateOf(prefs.getBoolean("sound", false)) }
    var popupPreview by remember { mutableStateOf(prefs.getBoolean("popup_preview", true)) }
    var dblSpacePeriod by remember { mutableStateOf(prefs.getBoolean("double_space_period", true)) }

    // ── Text Correction ───────────────────────────────────────────────────────
    var suggestions  by remember { mutableStateOf(prefs.getBoolean("suggestions", true)) }
    var autoCaps     by remember { mutableStateOf(prefs.getBoolean("auto_caps", true)) }
    var nextWord     by remember { mutableStateOf(prefs.getBoolean("next_word_suggestions", true)) }

    // ── Appearance ────────────────────────────────────────────────────────────
    var height       by remember { mutableFloatStateOf(prefs.getFloat("height", 1.0f)) }
    var showHints    by remember { mutableStateOf(prefs.getBoolean("show_hints", true)) }

    // ── Bodo Transliteration ─────────────────────────────────────────────────
    var learnWords   by remember { mutableStateOf(prefs.getBoolean("learn_words", true)) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor   = Surface1,
            title = { Text("Clear learned words?", color = TextPri, fontWeight = FontWeight.SemiBold) },
            text  = { Text("This will remove all words you have taught the keyboard and restore default suggestions.", color = TextSec, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLearnedWords()
                    showClearDialog = false
                }) {
                    Text("Clear", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSec)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPri, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(20.dp))

        // ── PREFERENCES ───────────────────────────────────────────────────────
        SectionHeader("Preferences")
        SettingsCard {
            ToggleRow(
                icon     = Icons.Default.Vibration,
                title    = "Vibration",
                subtitle = "Haptic feedback on each keypress",
                checked  = haptic
            ) { haptic = it; prefs.edit().putBoolean("haptic", it).apply() }

            Divider()

            ToggleRow(
                icon     = Icons.AutoMirrored.Filled.VolumeUp,
                title    = "Sound on keypress",
                subtitle = "Click sound when typing",
                checked  = sound
            ) { sound = it; prefs.edit().putBoolean("sound", it).apply() }

            Divider()

            ToggleRow(
                icon     = Icons.Default.TouchApp,
                title    = "Popup on keypress",
                subtitle = "Show a preview of the key above your finger",
                checked  = popupPreview
            ) { popupPreview = it; prefs.edit().putBoolean("popup_preview", it).apply() }

            Divider()

            ToggleRow(
                icon     = Icons.Default.SpaceBar,
                title    = "Double-space for period",
                subtitle = "Quickly insert a period by tapping space twice",
                checked  = dblSpacePeriod
            ) { dblSpacePeriod = it; prefs.edit().putBoolean("double_space_period", it).apply() }
        }

        Spacer(Modifier.height(20.dp))

        // ── TEXT CORRECTION ───────────────────────────────────────────────────
        SectionHeader("Text Correction")
        SettingsCard {
            ToggleRow(
                icon     = Icons.Default.AutoAwesome,
                title    = "Show suggestion strip",
                subtitle = "Display word suggestions above the keyboard",
                checked  = suggestions
            ) { suggestions = it; prefs.edit().putBoolean("suggestions", it).apply() }

            Divider()

            ToggleRow(
                icon     = Icons.Default.TextFields,
                title    = "Auto-capitalize",
                subtitle = "Capitalize the first letter of each sentence",
                checked  = autoCaps
            ) { autoCaps = it; prefs.edit().putBoolean("auto_caps", it).apply() }

            Divider()

            ToggleRow(
                icon     = Icons.Default.Spellcheck,
                title    = "Next-word suggestions",
                subtitle = "Suggest words based on previously typed text",
                checked  = nextWord
            ) { nextWord = it; prefs.edit().putBoolean("next_word_suggestions", it).apply() }

            Divider()

            ActionRow(
                icon     = Icons.AutoMirrored.Filled.MenuBook,
                title    = "Personal dictionary",
                subtitle = "View and manage your saved words"
            ) { /* future */ }
        }

        Spacer(Modifier.height(20.dp))

        // ── APPEARANCE ────────────────────────────────────────────────────────
        SectionHeader("Appearance")
        SettingsCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, null, tint = TextSec, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keyboard height", color = TextPri, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("Adjust key row height", fontSize = 12.sp, color = TextSec)
                    }
                    Text(
                        when {
                            height < 0.9f -> "Short"
                            height > 1.1f -> "Tall"
                            else          -> "Normal"
                        },
                        color      = Accent,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Slider(
                    value         = height,
                    onValueChange = { height = it; prefs.edit().putFloat("height", it).apply() },
                    valueRange    = 0.8f..1.3f,
                    steps         = 4,
                    colors        = SliderDefaults.colors(
                        thumbColor         = Accent,
                        activeTrackColor   = Accent,
                        inactiveTrackColor = Surface2
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Short",  fontSize = 11.sp, color = TextSec)
                    Text("Normal", fontSize = 11.sp, color = TextSec)
                    Text("Tall",   fontSize = 11.sp, color = TextSec)
                }
            }

            Divider()

            ToggleRow(
                icon     = Icons.Default.Visibility,
                title    = "Show key hints",
                subtitle = "Display secondary characters in the corner of keys",
                checked  = showHints
            ) { showHints = it; prefs.edit().putBoolean("show_hints", it).apply() }

            Divider()

            ActionRow(
                icon     = Icons.Default.KeyboardAlt,
                title    = "Keyboard theme",
                subtitle = "Dark"
            ) { /* future */ }
        }

        Spacer(Modifier.height(20.dp))

        // ── BODO TRANSLITERATION ──────────────────────────────────────────────
        SectionHeader("Bodo Transliteration")
        SettingsCard {
            ToggleRow(
                icon     = Icons.Default.School,
                title    = "Learn from typed text",
                subtitle = "Improve suggestions based on your writing style",
                checked  = learnWords
            ) { learnWords = it; prefs.edit().putBoolean("learn_words", it).apply() }

            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showClearDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clear learned words", color = ErrorRed, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text("Remove all personalized suggestions", fontSize = 12.sp, color = TextSec)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── ABOUT ─────────────────────────────────────────────────────────────
        SectionHeader("About")
        SettingsCard {
            InfoRow(
                icon     = Icons.Default.Info,
                title    = "Version",
                value    = "1.0.0"
            )

            Divider()

            ActionRow(
                icon     = Icons.Default.Lock,
                title    = "Privacy Policy",
                subtitle = ""
            ) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://bodokey.app/privacy")))
                }
            }

            Divider()

            ActionRow(
                icon     = Icons.Default.Feedback,
                title    = "Send Feedback",
                subtitle = ""
            ) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("mailto:feedback@bodokey.app")))
                }
            }

            Divider()

            ActionRow(
                icon     = Icons.AutoMirrored.Filled.MenuBook,
                title    = "Open Source Licenses",
                subtitle = ""
            ) { /* future */ }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text          = title.uppercase(),
        modifier      = Modifier.padding(start = 4.dp, bottom = 8.dp),
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Bold,
        color         = TextSec,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, DivLine, RoundedCornerShape(14.dp)),
        content = content
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 66.dp)
            .height(1.dp)
            .background(DivLine)
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = TextSec, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    color = TextPri, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (subtitle.isNotEmpty())
                Text(subtitle, color = TextSec, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked         = checked,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor    = Color.White,
                checkedTrackColor    = Accent,
                uncheckedThumbColor  = TextSec,
                uncheckedTrackColor  = Surface2,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick           = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = TextSec, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPri, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (subtitle.isNotEmpty())
                Text(subtitle, color = TextSec, fontSize = 12.sp)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint     = TextSec,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = TextSec, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = TextPri, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSec, fontSize = 14.sp)
    }
}
