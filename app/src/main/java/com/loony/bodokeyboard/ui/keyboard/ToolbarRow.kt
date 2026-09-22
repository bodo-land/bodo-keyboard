package com.loony.bodokeyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loony.bodokeyboard.data.KeyboardMode
import com.loony.bodokeyboard.ui.theme.AccentMint
import com.loony.bodokeyboard.ui.theme.DividerC
import com.loony.bodokeyboard.ui.theme.KbBg
import com.loony.bodokeyboard.ui.theme.KeyTxt
import com.loony.bodokeyboard.ui.theme.KeyTxtDark

private val ToolIconSize = 20.dp

@Composable
internal fun ToolbarRow(
    mode: KeyboardMode,
    isSuggestionsEnabled: Boolean,
    onKeyClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(KbBg)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Leftmost circular button (accented)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AccentMint)
                .clickable { onKeyClick("COLLAPSE") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Collapse keyboard",
                tint = KeyTxtDark,
                modifier = Modifier.size(18.dp)
            )
        }

        // Toolbar Icons
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolBtn(
                icon = Icons.Default.AutoAwesome,
                contentDescription = "Toggle suggestions",
                isHighlight = isSuggestionsEnabled
            ) { onKeyClick("SUGGESTION_TOGGLE") }

            // Language switcher: shows the language you'll switch TO
            if (mode == KeyboardMode.BODO) {
                LangBtn("EN")  { onKeyClick("SWITCH_EN") }
            } else {
                LangBtn("बर'") { onKeyClick("SWITCH_BODO") }
            }

            ToolBtn(icon = Icons.Default.EmojiEmotions, contentDescription = "Switch to emoji keyboard") { onKeyClick("EMOJI_SWITCH") }

            ToolBtn(label = "GIF") { onKeyClick("GIF_SWITCH") }
            ToolBtn(icon = Icons.AutoMirrored.Filled.Assignment, contentDescription = "Paste") { onKeyClick("PASTE") }
        }

        // Divider and more
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(DividerC)
            )
            ToolBtn(icon = Icons.Default.MoreHoriz, contentDescription = "Open settings") { onKeyClick("SETTINGS_OPEN") }
            ToolBtn(icon = Icons.Default.Mic, contentDescription = "Voice input") { /* voice */ }
        }
    }
}

@Composable
private fun LangBtn(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = KeyTxt,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
internal fun ToolBtn(
    icon: ImageVector? = null,
    label: String? = null,
    contentDescription: String? = null,
    isHighlight: Boolean = false,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isHighlight) Modifier.background(AccentMint.copy(alpha = 0.25f)) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isHighlight) AccentMint else KeyTxt,
                modifier = Modifier.size(ToolIconSize)
            )
        } else if (label != null) {
            Text(
                text = label,
                color = if (isHighlight) AccentMint else KeyTxt,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
