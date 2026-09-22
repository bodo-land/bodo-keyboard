package com.loony.bodokeyboard.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── Keyboard palette — Exactly like the provided Gboard screenshot ─────────────────────────────

internal val KbBg       = Color(0xFF131314)   // Deep black/grey background
internal val KeyNorm    = Color(0xFF2D2E30)   // Normal key background (charcoal)
internal val KeyPressed = Color(0xFF424346)   // Pressed state
internal val KeySpec    = Color(0xFF1B1B1B)   // Shift, Backspace color
internal val KeySpecP   = Color(0xFF2D2E30)   

// Accents from the image
internal val AccentMint = Color(0xFF84E3B2)   // The bright mint green
internal val SagePill   = Color(0xFFBDD2C1)   // Brighter sage for ?123 and ABC buttons

internal val EnterBg    = AccentMint          // Enter button background
internal val CapsActive = AccentMint          // CapsLock indicator

internal val SuggBg     = Color(0xFF131314)
internal val SuggTxt    = Color(0xFFE8EAED)
internal val DividerC   = Color(0xFF3C4043)
internal val ChipHighBg = AccentMint
internal val ChipBg     = Color(0xFF303134)
internal val KeyTxt     = Color(0xFFE8EAED)   // Light text for dark keys
internal val KeyTxtDark = Color(0xFF131314)   // Dark text for light keys (Enter, ?123)
internal val HintTxt    = Color(0xFF9AA0A6)
internal val ToolTxt    = Color(0xFFE8EAED)

// ── Shapes ────────────────────────────────────────────────────────────────────

internal val KeyShape      = RoundedCornerShape(6.dp) // Gboard keys are quite rounded // change: border_radius of keys
internal val PillShape     = RoundedCornerShape(50)    // For ?123 and Enter
internal val CircleShape   = RoundedCornerShape(50)    // For toolbar left icon
internal val ChipShape     = RoundedCornerShape(50)
