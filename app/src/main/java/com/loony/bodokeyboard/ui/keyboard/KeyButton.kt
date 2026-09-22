@file:OptIn(ExperimentalFoundationApi::class)

package com.loony.bodokeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.loony.bodokeyboard.data.KeyboardMode
import com.loony.bodokeyboard.ui.theme.AccentMint
import com.loony.bodokeyboard.ui.theme.CapsActive
import com.loony.bodokeyboard.ui.theme.HintTxt
import com.loony.bodokeyboard.ui.theme.KeyNorm
import com.loony.bodokeyboard.ui.theme.KeyPressed
import com.loony.bodokeyboard.ui.theme.KeyShape
import com.loony.bodokeyboard.ui.theme.KeySpec
import com.loony.bodokeyboard.ui.theme.KeySpecP
import com.loony.bodokeyboard.ui.theme.KeyTxt
import com.loony.bodokeyboard.ui.theme.KeyTxtDark
import com.loony.bodokeyboard.ui.theme.PillShape
import com.loony.bodokeyboard.ui.theme.SagePill
import com.loony.bodokeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun KeyButton(
    key: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    mode: KeyboardMode,
    isCapsLock: Boolean,
    viewModel: KeyboardViewModel?,
    onSpaceDrag: (Int) -> Unit = {},
    onLongPress: ((String) -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // rememberUpdatedState lets stable lambdas below always call the latest callback
    // without being recreated themselves on every recomposition.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnSpaceDrag by rememberUpdatedState(onSpaceDrag)
    val currentView by rememberUpdatedState(view)

    val stableOnClick = remember {
        {
            if (viewModel?.hapticEnabled?.value == true)
                currentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            currentOnClick()
        }
    }

    // Keyed on nullability: onLongPress is either always provided or never provided per key.
    val stableOnLongClick = remember(onLongPress != null) {
        if (onLongPress == null) null
        else {
            {
                currentView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                currentOnLongPress?.invoke(key)
                Unit
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed && key == "BACKSPACE") {
            delay(400)
            while (true) {
                onClick()
                delay(60)
            }
        }
    }

    val isSpecial   = key in setOf("SHIFT", "BACKSPACE", "SYM", "ABC", "MODE_SWITCH", "SYM_PAGE")
    val isEnter     = key == "ENTER"
    val isPill      = key in setOf("SYM", "ABC", "ENTER")
    val isShiftCaps = key == "SHIFT" && isCapsLock

    val bgColor = when {
        key == "ENTER"         -> if (isPressed) AccentMint.copy(alpha = 0.8f) else AccentMint
        key in setOf("SYM", "ABC") -> if (isPressed) SagePill.copy(alpha = 0.8f) else SagePill
        isShiftCaps            -> CapsActive
        isSpecial              -> if (isPressed) KeySpecP else KeySpec
        else                   -> if (isPressed) KeyPressed else KeyNorm
    }

    // change: key_height
    val height = 38.dp * (viewModel?.keyboardHeightMultiplier?.value ?: 1f)

    val icon: ImageVector? = when (key) {
        "BACKSPACE" -> Icons.AutoMirrored.Filled.Backspace
        "ENTER"     -> resolveEnterIcon(viewModel)
        "SHIFT"     -> when {
            isCapsLock                -> Icons.Default.KeyboardDoubleArrowUp
            mode == KeyboardMode.BODO -> null
            else                      -> Icons.Default.ArrowUpward
        }
        else -> null
    }

    val label = when (key) {
        "SHIFT"        -> if (mode == KeyboardMode.BODO && !isCapsLock) "आ/क" else ""
        "SPACE"        -> when (mode) {
            KeyboardMode.BODO     -> "बर'"
            KeyboardMode.TRANSLIT -> "बर' (Translit)"
            KeyboardMode.ENGLISH  -> "English"
            KeyboardMode.NUMERIC  -> "Space"
            else                  -> ""
        }
        "SYM"          -> "?123"
        "SYM_PAGE"     -> if (viewModel?.isSymbols2?.value == true) "?123" else "=\\<"
        "ABC"          -> "ABC"
        "BACKSPACE"    -> ""
        "MODE_SWITCH"  -> if (mode == KeyboardMode.BODO) "EN" else "बर'"
        "EMOJI_SWITCH" -> "😊"
        "ENTER"        -> ""
        else           -> key
    }

    val contentColor = when {
        key == "ENTER" || key in setOf("SYM", "ABC") -> KeyTxtDark
        else -> KeyTxt
    }

    val fontSize = when {
        key == "SHIFT" && mode == KeyboardMode.BODO         -> 13.sp
        key in setOf("SHIFT", "BACKSPACE", "ENTER")         -> 22.sp
        key in setOf("MODE_SWITCH", "SYM", "ABC")           -> 16.sp
        key == "SPACE"                                       -> 13.sp
        key.length > 2                                       -> 12.sp
        else                                                 -> 20.sp
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(if (isPill) PillShape else KeyShape)
            .background(bgColor)
            .then(
                if (key == "SPACE") Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd    = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onHorizontalDrag = { _, amount ->
                            dragAccumulator += amount
                            if (abs(dragAccumulator) > 40f) {
                                currentOnSpaceDrag(if (dragAccumulator > 0) 1 else -1)
                                dragAccumulator = 0f
                                currentView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    )
                } else Modifier
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = stableOnClick,
                onLongClick       = stableOnLongClick,
            )
    ) {
        if (isPressed && !isSpecial && !isEnter && key != "SPACE") {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -110) // Adjust based on key height
            ) {
                Box(
                    modifier = Modifier
                        .size(height * 1.3f, height * 1.8f)
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                        .background(KeyNorm, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (icon != null) {
            Icon(
                imageVector        = icon,
                contentDescription = when (key) {
                    "BACKSPACE" -> "Backspace"
                    "ENTER"     -> "Enter"
                    "SHIFT"     -> "Shift"
                    else        -> null
                },
                tint               = contentColor,
                modifier           = Modifier.align(Alignment.Center).size(24.dp)
            )
        } else {
            Text(
                text       = label,
                modifier   = Modifier.align(Alignment.Center),
                fontSize   = fontSize,
                fontWeight = if (isPill) FontWeight.Medium else FontWeight.Normal,
                color      = contentColor
            )
        }

        if (hint != null) {
            Text(
                text       = hint,
                modifier   = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 6.dp),
                fontSize   = 10.sp,
                color      = HintTxt,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

private fun resolveEnterIcon(viewModel: KeyboardViewModel?): ImageVector {
    val inputType   = viewModel?.editorInfo?.inputType ?: 0
    val isMultiLine = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0
    if (isMultiLine) return Icons.AutoMirrored.Filled.KeyboardReturn
    return when (viewModel?.imeAction?.value) {
        EditorInfo.IME_ACTION_SEARCH -> Icons.Default.Search
        EditorInfo.IME_ACTION_SEND   -> Icons.AutoMirrored.Filled.Send
        EditorInfo.IME_ACTION_GO     -> Icons.AutoMirrored.Filled.ArrowForward
        EditorInfo.IME_ACTION_DONE   -> Icons.Default.Done
        else                         -> Icons.AutoMirrored.Filled.KeyboardReturn
    }
}
