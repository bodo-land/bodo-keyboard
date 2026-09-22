package com.loony.bodokeyboard.ui.preview

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loony.bodokeyboard.KeyboardScreen
import com.loony.bodokeyboard.data.KeyboardMode
import com.loony.bodokeyboard.ui.theme.DivLine
import com.loony.bodokeyboard.ui.theme.Surface1
import com.loony.bodokeyboard.ui.theme.TextPri
import com.loony.bodokeyboard.ui.theme.TextSec
import com.loony.bodokeyboard.viewmodel.KeyboardViewModel

/**
 * Interactive "Try it" preview screen — shows a live text field above a fully
 * functional keyboard. All key handling is local to this composable; nothing
 * is sent to any external InputConnection.
 */
@Composable
fun PreviewView() {
    val viewModel: KeyboardViewModel = viewModel()
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.initDatabase(context)
        val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        viewModel.hapticEnabled.value            = prefs.getBoolean("haptic", true)
        viewModel.soundEnabled.value             = prefs.getBoolean("sound", false)
        viewModel.keyboardHeightMultiplier.value = prefs.getFloat("height", 1.0f)

        val bodoWords = try {
            context.assets.open("bodo_words.txt").bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        } catch (_: Exception) { emptyList() }
        val englishWords = try {
            context.assets.open("english_words.txt").bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        } catch (_: Exception) { emptyList() }
        viewModel.setWordLists(bodoWords, englishWords)
    }

    Column(Modifier.fillMaxSize()) {
        // ── Live preview text field ───────────────────────────────────────────
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Try the Bodo Keyboard below",
                    color    = TextSec,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    shape  = RoundedCornerShape(12.dp),
                    color  = Surface1,
                    border = BorderStroke(1.dp, DivLine)
                ) {
                    Box(Modifier.padding(16.dp)) {
                        if (text.isEmpty()) {
                            Text("Type something...", color = TextSec.copy(alpha = 0.5f))
                        }
                        Text(text, color = TextPri, fontSize = 18.sp)
                    }
                }
            }
        }

        // ── Keyboard ──────────────────────────────────────────────────────────
        KeyboardScreen(
            viewModel = viewModel,
            onKeyClick = { key -> handlePreviewKey(key, viewModel, text) { text = it } },
            onSuggestionClick = { word ->
                val lastSpace = text.lastIndexOf(' ')
                text = if (lastSpace != -1) text.substring(0, lastSpace + 1) + word + " "
                       else word + " "
                viewModel.clearTranslitBuffer()
                viewModel.updateSuggestions("")
            }
        )
    }
}

/** Processes a key press in the preview screen, updating [text] via [onTextChange]. */
private fun handlePreviewKey(
    key: String,
    viewModel: KeyboardViewModel,
    text: String,
    onTextChange: (String) -> Unit
) {
    when (key) {
        "BACKSPACE" -> {
            if (viewModel.isTranslitMode() && viewModel.translitBuffer.value.isNotEmpty()) {
                viewModel.translitBackspace()
                onTextChange(text.dropLast(1))
            } else if (text.isNotEmpty()) {
                onTextChange(text.dropLast(1))
            }
        }
        "SPACE" -> {
            val flushed = if (viewModel.isTranslitMode()) viewModel.flushTranslit() else ""
            onTextChange(text + flushed + " ")
            viewModel.updateSuggestions("")
        }
        "ENTER" -> {
            val flushed = if (viewModel.isTranslitMode()) viewModel.flushTranslit() else ""
            onTextChange(text + flushed + "\n")
            viewModel.updateSuggestions("")
        }
        "SHIFT"      -> viewModel.toggleShift()
        "SYM"        -> viewModel.toggleSymbols()
        "SYM_PAGE"   -> viewModel.toggleSymbolsPage()
        "ABC" -> {
            if (viewModel.keyboardMode.value == KeyboardMode.EMOJI ||
                viewModel.keyboardMode.value == KeyboardMode.GIF) {
                viewModel.setMode(KeyboardMode.BODO)
            } else {
                viewModel.toggleSymbols()
            }
        }
        "MODE_SWITCH" -> {
            if (viewModel.isTranslitMode()) onTextChange(text + viewModel.flushTranslit())
            viewModel.setMode(
                if (viewModel.keyboardMode.value == KeyboardMode.BODO) KeyboardMode.ENGLISH
                else KeyboardMode.BODO
            )
        }
        "SWITCH_BODO" -> {
            if (viewModel.isTranslitMode()) onTextChange(text + viewModel.flushTranslit())
            viewModel.setMode(KeyboardMode.BODO)
        }
        "SWITCH_EN" -> {
            if (viewModel.isTranslitMode()) onTextChange(text + viewModel.flushTranslit())
            viewModel.setMode(KeyboardMode.ENGLISH)
        }
        "TRANSLIT_TOGGLE" -> {
            if (viewModel.isTranslitMode()) onTextChange(text + viewModel.flushTranslit())
            viewModel.setMode(
                if (viewModel.keyboardMode.value == KeyboardMode.TRANSLIT) KeyboardMode.ENGLISH
                else KeyboardMode.TRANSLIT
            )
        }
        "EMOJI_SWITCH" -> {
            if (viewModel.isTranslitMode()) onTextChange(text + viewModel.flushTranslit())
            viewModel.setMode(KeyboardMode.EMOJI)
        }
        "GIF_SWITCH" -> {
            if (viewModel.isTranslitMode()) onTextChange(text + viewModel.flushTranslit())
            viewModel.setMode(KeyboardMode.GIF)
        }
        "SETTINGS_OPEN" -> { /* handled by parent tab navigation */ }
        else -> {
            if (viewModel.isTranslitMode() && key.length == 1 && key[0].isLetter()) {
                val result = viewModel.feedTranslit(key[0])
                val bufLen = viewModel.translitBuffer.value.length
                onTextChange(text.substring(0, (text.length - bufLen + 1).coerceAtLeast(0)) + result.pending)
            } else {
                onTextChange(text + key)
                viewModel.autoResetShift()
            }
        }
    }
    if (!viewModel.isTranslitMode()) {
        val lastWord = text.split(" ", "\n").lastOrNull() ?: ""
        viewModel.updateSuggestions(lastWord)
    }
}
