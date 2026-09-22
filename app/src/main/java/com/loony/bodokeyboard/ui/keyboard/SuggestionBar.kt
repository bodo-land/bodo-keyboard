package com.loony.bodokeyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loony.bodokeyboard.ui.theme.ChipBg
import com.loony.bodokeyboard.ui.theme.ChipHighBg
import com.loony.bodokeyboard.ui.theme.ChipShape
import com.loony.bodokeyboard.ui.theme.DividerC
import com.loony.bodokeyboard.ui.theme.SuggBg
import com.loony.bodokeyboard.ui.theme.SuggTxt

/**
 * The strip above the keyboard rows. Shows either:
 *  - Word suggestions (3 equal slots, no borders)
 *  - A long-press alternate picker for the given key
 */
@Composable
internal fun SuggestionBar(
    suggestions: List<String>,
    longPressKey: String?,
    longPressAlts: List<String>,
    onSuggestion: (String) -> Unit,
    onAlternate: (String) -> Unit,
    onDismissAlts: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(SuggBg)
    ) {
        if (longPressKey != null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AltChip(text = longPressKey, isMain = true,  onClick = { onAlternate(longPressKey) })
                longPressAlts.forEach { alt ->
                    AltChip(text = alt, isMain = false, onClick = { onAlternate(alt) })
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(ChipShape)
                        .background(DividerC)
                        .clickable(onClick = onDismissAlts),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = SuggTxt, fontSize = 12.sp)
                }
            }
        } else {
            // Reverted to 3 equal slots as requested, but with bigger text
            val padded = (suggestions + List(3) { "" }).take(3)
            Row(modifier = Modifier.fillMaxSize()) {
                padded.forEach { word ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (word.isNotEmpty()) Modifier.clickable { onSuggestion(word) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (word.isNotEmpty()) {
                            Text(
                                text       = word,
                                color      = SuggTxt,
                                fontSize   = 18.sp, // Bigger text for Bodo readability
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A single chip in the alternate character picker. */
@Composable
private fun AltChip(text: String, isMain: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .height(28.dp)
            .clip(ChipShape)
            .background(if (isMain) ChipHighBg else ChipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
