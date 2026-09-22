package com.loony.bodokeyboard.ui.setup

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loony.bodokeyboard.ui.theme.Accent
import com.loony.bodokeyboard.ui.theme.DivLine
import com.loony.bodokeyboard.ui.theme.GreenDone
import com.loony.bodokeyboard.ui.theme.Surface1
import com.loony.bodokeyboard.ui.theme.Surface2
import com.loony.bodokeyboard.ui.theme.TextPri
import com.loony.bodokeyboard.ui.theme.TextSec

@Composable
fun SetupView() {
    val context     = LocalContext.current
    var isEnabled  by remember { mutableStateOf(false) }
    var isSelected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            isEnabled  = imm.enabledInputMethodList.any { it.packageName == context.packageName }
            val cur    = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            isSelected = cur?.contains(context.packageName) == true
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // ── App icon ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Accent.copy(alpha = 0.15f))
                .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("बर'", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Accent)
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Bodo Keyboard",
            fontSize   = 26.sp,
            fontWeight = FontWeight.Bold,
            color      = TextPri,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "बड' भाषा • Devanagari Script",
            fontSize  = 14.sp,
            color     = TextSec,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        // ── Step 1 ────────────────────────────────────────────────────────────
        SetupStepCard(
            step        = 1,
            title       = "Enable Bodo Keyboard",
            description = "Add Bodo Keyboard to your list of input methods in System Settings.",
            actionLabel = "Open Settings",
            icon        = Icons.AutoMirrored.Filled.OpenInNew,
            isDone      = isEnabled,
            onClick     = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        )

        Spacer(Modifier.height(12.dp))

        // ── Step 2 ────────────────────────────────────────────────────────────
        SetupStepCard(
            step        = 2,
            title       = "Select as Default",
            description = "Choose Bodo Keyboard as your active input method when prompted.",
            actionLabel = "Select",
            icon        = Icons.Default.TouchApp,
            isDone      = isSelected,
            onClick     = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        )

        if (isEnabled && isSelected) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GreenDone.copy(alpha = 0.10f))
                    .border(1.dp, GreenDone.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, null, tint = GreenDone, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "You're all set! Bodo Keyboard is active.",
                    color      = GreenDone,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 14.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Usage tips ────────────────────────────────────────────────────────
        TipsCard()

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SetupStepCard(
    step: Int,
    title: String,
    description: String,
    actionLabel: String,
    icon: ImageVector,
    isDone: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(
                1.dp,
                if (isDone) GreenDone.copy(alpha = 0.3f) else DivLine,
                RoundedCornerShape(14.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Step number / done circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isDone) GreenDone.copy(alpha = 0.15f) else Surface2)
                .border(1.dp, if (isDone) GreenDone.copy(alpha = 0.4f) else DivLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(Icons.Default.Check, null, tint = GreenDone, modifier = Modifier.size(16.dp))
            } else {
                Text("$step", color = TextSec, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = if (isDone) TextSec else TextPri,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(description, fontSize = 13.sp, color = TextSec, lineHeight = 18.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDone) Surface2 else Accent)
                    .clickable(
                        onClick           = onClick,
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDone) Icons.Default.Check else icon,
                        contentDescription = null,
                        tint = if (isDone) TextSec else Color(0xFF1A1A1A),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isDone) "Done" else actionLabel,
                        color      = if (isDone) TextSec else Color(0xFF1A1A1A),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TipsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, DivLine, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.KeyboardAlt,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Tips", color = TextPri, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))
        TipRow("Switch between Bodo and English using the language button in the toolbar.")
        TipRow("Long-press keys to see alternate characters.")
        TipRow("Swipe left on the space bar to move the cursor.")
        TipRow("Tap the star icon in the toolbar to toggle word suggestions.")
    }
}

@Composable
private fun TipRow(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text("•", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextSec, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
