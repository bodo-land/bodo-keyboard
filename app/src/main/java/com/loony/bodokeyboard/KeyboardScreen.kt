package com.loony.bodokeyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loony.bodokeyboard.data.KeyboardMode
import com.loony.bodokeyboard.ui.emoji.EmojiKeyboard
import com.loony.bodokeyboard.ui.emoji.GifKeyboard
import com.loony.bodokeyboard.ui.keyboard.KeyButton
import com.loony.bodokeyboard.ui.keyboard.SuggestionBar
import com.loony.bodokeyboard.ui.keyboard.ToolbarRow
import com.loony.bodokeyboard.ui.theme.KbBg
import com.loony.bodokeyboard.viewmodel.KeyboardViewModel

data class KeyboardState(
    val mode: KeyboardMode,
    val isShifted: Boolean,
    val isCapsLock: Boolean,
    val isSymbols: Boolean,
    val isSymbols2: Boolean,
    val isEmailField: Boolean
) {
    val isQwerty: Boolean
        get() = mode == KeyboardMode.ENGLISH || mode == KeyboardMode.TRANSLIT
}

data class KeyRowModel(
    val keys: List<String>,
    val horizontalSpacing: Dp = 7.dp,
    val verticalPadding: Dp = 0.dp,
    val horizontalPadding: Dp = 0.dp,
    val isRow2: Boolean = false 
)

interface KeyboardLayout {
    fun rows(): List<KeyRowModel>
}

private class EnglishLayout(
    private val isShifted: Boolean,
    private val isEmailField: Boolean
) : KeyboardLayout {

    private val bottomRow = if (isEmailField)
        listOf("SYM", ",", "SPACE", "@", "ENTER")
    else
        listOf("SYM", ",", "SPACE", ".", "ENTER")

    override fun rows() = if (!isShifted) listOf(
        KeyRowModel(keys = listOf("q","w","e","r","t","y","u","i","o","p")),
        KeyRowModel(keys = listOf("a","s","d","f","g","h","j","k","l"), isRow2 = true),
        KeyRowModel(keys = listOf("SHIFT","z","x","c","v","b","n","m","BACKSPACE")),
        KeyRowModel(keys = bottomRow)
    ) else listOf(
        KeyRowModel(keys = listOf("Q","W","E","R","T","Y","U","I","O","P")),
        KeyRowModel(keys = listOf("A","S","D","F","G","H","J","K","L"), isRow2 = true),
        KeyRowModel(keys = listOf("SHIFT","Z","X","C","V","B","N","M","BACKSPACE")),
        KeyRowModel(keys = bottomRow)
    )
}

private class SymbolsLayout : KeyboardLayout {
    override fun rows() = listOf(
        KeyRowModel(keys = listOf("1","2","3","4","5","6","7","8","9","0")),
        KeyRowModel(keys = listOf("@","#","₹","%","&","-","+","(",")")),
        KeyRowModel(keys = listOf("SYM_PAGE","*","\"","'",":",";","!","?","BACKSPACE")),
        KeyRowModel(keys = listOf("ABC",",","SPACE",".","ENTER"))
    )
}

private class Symbols2Layout : KeyboardLayout {
    override fun rows() = listOf(
        KeyRowModel(keys = listOf("~","`","|","•","√","π","÷","×","{","}")),
        KeyRowModel(keys = listOf("£","¢","€","¥","^","°","=","_","\\")),
        KeyRowModel(keys = listOf("SYM_PAGE","[","]","«","»","!","?","BACKSPACE")),
        KeyRowModel(keys = listOf("ABC",",","SPACE",".","ENTER"))
    )
}

private class BodoLayout(
    private val isShifted: Boolean,
    private val isEmailField: Boolean
) : KeyboardLayout {

    private val bottomRow = if (isEmailField)
        listOf("SYM", ",", "SPACE", "@", "ENTER")
    else
        listOf("SYM", ",", "SPACE", "।", "ENTER")

    override fun rows() = if (isShifted) listOf(
        KeyRowModel(keys = listOf("अ","आ","इ","ई","उ","ऊ","ऋ","ए","ऐ","ओ","औ")),
        KeyRowModel(keys = listOf("ा","ि","ी","ु","ू","ृ","े","ै","ो","ौ")),
        KeyRowModel(keys = listOf("ं","ः","ँ","्","।","॥","०","१","२","३")),
        KeyRowModel(keys = listOf("SHIFT","४","५","६","७","८","९","BACKSPACE")),
        KeyRowModel(keys = bottomRow)
    ) else listOf(
        KeyRowModel(keys = listOf("क","ख","ग","घ","ङ","च","छ","ज","झ","ञ")),
        KeyRowModel(keys = listOf("ट","ठ","ड","ढ","ण","त","थ","द","ध","न")),
        KeyRowModel(keys = listOf("प","फ","ब","भ","म","य","र","ल","व")),
        KeyRowModel(keys = listOf("SHIFT","श","ष","स","ह","ड़","ढ़","क्ष","BACKSPACE"), isRow2 = true),
        KeyRowModel(keys = bottomRow)
    )
}

private class NumericLayout : KeyboardLayout {
    override fun rows() = listOf(
        KeyRowModel(keys = listOf("1", "2", "3"), isRow2 = true),
        KeyRowModel(keys = listOf("4", "5", "6"), isRow2 = true),
        KeyRowModel(keys = listOf("7", "8", "9"), isRow2 = true),
        KeyRowModel(keys = listOf("ABC", "SPACE", "0", "BACKSPACE", "ENTER"))
    )
}

/** Accented Latin alternates offered on long-press, keyed by lowercase base letter. */
private val ALTERNATE_CHARS: Map<String, List<String>> = mapOf(
    "a" to listOf("à", "á", "â", "ã", "ä", "å", "ā"),
    "e" to listOf("è", "é", "ê", "ë", "ē"),
    "i" to listOf("ì", "í", "î", "ï", "ī"),
    "o" to listOf("ò", "ó", "ô", "õ", "ö", "ø", "ō"),
    "u" to listOf("ù", "ú", "û", "ü", "ū"),
    "n" to listOf("ñ"),
    "c" to listOf("ç", "ć"),
    "s" to listOf("ś", "š"),
    "y" to listOf("ý", "ÿ"),
    "z" to listOf("ź", "ž", "ż"),
    "g" to listOf("ğ"),
    "l" to listOf("ł")
)

private fun alternatesFor(key: String): List<String> {
    if (key.length != 1) return emptyList()
    val lower = key.lowercase()
    val alts = ALTERNATE_CHARS[lower] ?: return emptyList()
    return if (key == lower) alts else alts.map { it.uppercase() }
}

private fun resolveLayout(state: KeyboardState): KeyboardLayout = when {
    state.mode == KeyboardMode.NUMERIC -> NumericLayout()
    state.isSymbols2 -> Symbols2Layout()
    state.isSymbols  -> SymbolsLayout()
    state.isQwerty   -> EnglishLayout(state.isShifted, state.isEmailField)
    else             -> BodoLayout(state.isShifted, state.isEmailField)
}

@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    onKeyClick: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onSpaceDrag: (Int) -> Unit = {}
) {
    val state by remember {
        derivedStateOf {
            KeyboardState(
                mode         = viewModel.keyboardMode.value,
                isShifted    = viewModel.isShifted.value,
                isCapsLock   = viewModel.isCapsLock.value,
                isSymbols    = viewModel.isSymbols.value,
                isSymbols2   = viewModel.isSymbols2.value,
                isEmailField = viewModel.isEmailField.value
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KbBg)
            .navigationBarsPadding() // Robust padding for navigation bars
    ) {
        when (state.mode) {
            KeyboardMode.EMOJI -> EmojiKeyboard(viewModel, onKeyClick)
            KeyboardMode.GIF   -> GifKeyboard(viewModel, onKeyClick)
            else -> KeyboardContent(state, viewModel, onKeyClick, onSuggestionClick, onSpaceDrag)
        }
    }
}

@Composable
private fun KeyboardContent(
    state: KeyboardState,
    viewModel: KeyboardViewModel,
    onKeyClick: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onSpaceDrag: (Int) -> Unit
) {
    val layout = remember(state) { resolveLayout(state) }
    val rows = layout.rows()
    var longPressedKey by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        if (viewModel.suggestionsEnabled.value || longPressedKey != null) {
            SuggestionBar(
                suggestions = viewModel.suggestions.value,
                longPressKey = longPressedKey,
                longPressAlts = longPressedKey?.let { alternatesFor(it) } ?: emptyList(),
                onSuggestion = onSuggestionClick,
                onAlternate = { alt -> onKeyClick(alt); longPressedKey = null },
                onDismissAlts = { longPressedKey = null }
            )
        }

        ToolbarRow(
            mode = state.mode,
            isSuggestionsEnabled = viewModel.suggestionsEnabled.value,
            onKeyClick = onKeyClick
        )

        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(row.horizontalSpacing)
                ) {
                    if (row.isRow2) Spacer(Modifier.weight(0.5f))

                    row.keys.forEach { key ->
                        val hasAlternates = alternatesFor(key).isNotEmpty()
                        KeyButton(
                            key = key,
                            hint = if (state.isQwerty && !state.isSymbols && rows.first() == row) getHint(key) else null,
                            modifier = Modifier.weight(keyWeight(key, state.mode)),
                            mode = state.mode,
                            isCapsLock = state.isCapsLock,
                            viewModel = viewModel,
                            onSpaceDrag = onSpaceDrag,
                            // Only keys with accented alternates get a long-press
                            // handler — SPACE needs its drag gesture free of
                            // competing long-click detection, and BACKSPACE
                            // already auto-repeats on hold.
                            onLongPress = if (hasAlternates) { k -> longPressedKey = k } else null,
                            onClick = { onKeyClick(key) }
                        )
                    }

                    if (row.isRow2) Spacer(Modifier.weight(0.5f))
                }
            }
        }
    }
}

private fun getHint(key: String): String? = when(key.lowercase()) {
    "q" -> "1" ; "w" -> "2" ; "e" -> "3" ; "r" -> "4" ; "t" -> "5"
    "y" -> "6" ; "u" -> "7" ; "i" -> "8" ; "o" -> "9" ; "p" -> "0"
    else -> null
}

private fun keyWeight(key: String, mode: KeyboardMode): Float {
    if (mode == KeyboardMode.NUMERIC) {
        return when (key) {
            "ABC", "ENTER" -> 0.5f
            else -> 1f
        }
    }
    return when (key) {
        "SPACE" -> 4.5f
        "ENTER" -> 1.5f
        "SHIFT", "BACKSPACE", "SYM", "ABC" -> 1.5f
        "MODE_SWITCH", "EMOJI_SWITCH" -> 1.0f
        "SYM_PAGE", ".com" -> 1.2f
        else -> 1f
    }
}
