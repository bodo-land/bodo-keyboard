package com.loony.bodokeyboard.ui.emoji

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.loony.bodokeyboard.EnglishLayout
import com.loony.bodokeyboard.KeyboardRows
import com.loony.bodokeyboard.data.KeyboardMode
import com.loony.bodokeyboard.ui.keyboard.KeyButton
import com.loony.bodokeyboard.ui.keyboard.ToolBtn
import com.loony.bodokeyboard.ui.theme.AccentBlue
import com.loony.bodokeyboard.ui.theme.ChipShape
import com.loony.bodokeyboard.ui.theme.DividerC
import com.loony.bodokeyboard.ui.theme.EnterBg
import com.loony.bodokeyboard.ui.theme.KbBg
import com.loony.bodokeyboard.ui.theme.KeyNorm
import com.loony.bodokeyboard.ui.theme.KeySpec
import com.loony.bodokeyboard.ui.theme.SuggTxt
import com.loony.bodokeyboard.ui.theme.ToolTxt
import com.loony.bodokeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.launch

/**
 * Enhanced full-screen emoji picker panel.
 */
@Composable
fun EmojiKeyboard(viewModel: KeyboardViewModel, onKeyClick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    // Skin tone state
    var longPressedEmoji by remember { mutableStateOf<String?>(null) }

    // Freeze the recents order for the lifetime of this panel instance. Without
    // this, tapping an emoji moves it to slot 1 immediately (addRecentEmoji),
    // reshuffling the grid under the user's next tap — a quick double-tap on
    // the 7th emoji would actually hit whatever slid into that position after
    // the first tap. The live order is still persisted via addRecentEmoji; it
    // just isn't reflected here until the emoji panel is reopened.
    val recentEmojisSnapshot = remember { viewModel.recentEmojis.toList() }

    // Enhanced Search Engine: Multi-word support + Ranking (Google-style)
    val searchResults: List<String>? = searchQuery.trim().takeIf { it.isNotEmpty() }?.let { q ->
        val queryTokens = q.lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val emojiScores = mutableMapOf<String, Int>()

        // 1. Invert and Match
        for ((keyword, emojis) in EMOJI_KEYWORDS) {
            val keywordLower = keyword.lowercase()
            for (token in queryTokens) {
                if (keywordLower.contains(token)) {
                    // Match found! Boost emojis in this category.
                    // Emojis matching multiple query words get higher scores.
                    for (emoji in emojis) {
                        emojiScores[emoji] = (emojiScores[emoji] ?: 0) + 1
                    }
                }
            }
        }

        // 2. Sort by Relevance (Score)
        emojiScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    fun handleEmojiClick(emoji: String) {
        viewModel.addRecentEmoji(emoji)
        onKeyClick(emoji)
        longPressedEmoji = null
    }

    // Indices for continuous scroll mapping
    val categoryStartIndices = remember {
        val indices = mutableMapOf<Int, Int>()
        var currentIdx = 0

        // Recently used
        indices[-1] = 0
        currentIdx += 1 + recentEmojisSnapshot.size

        EMOJI_CATEGORIES.forEachIndexed { idx, pair ->
            indices[idx] = currentIdx
            currentIdx += 1 + pair.second.size
        }
        indices
    }

    // Determine active category for tab highlighting
    val activeCategory by remember {
        derivedStateOf {
            if (isSearching) -2 
            else {
                val firstVisible = gridState.firstVisibleItemIndex
                categoryStartIndices.entries
                    .filter { it.value <= firstVisible }
                    .maxByOrNull { it.value }?.key ?: -1
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .background(KbBg)
                .fillMaxWidth()
                .height(if (isSearching) 340.dp else 290.dp)
        ) {
            // ── Gboard Style Header ───────────────────────────────────────────
            // Back arrow, search pill, and (when idle) the category tabs all
            // share a single row — matching Gboard, which never splits these
            // across two lines. While actively searching, Gboard keeps this as
            // a static "Search emoji" title — the live, editable search field
            // lives further down, right above the keyboard, not up here.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ToolTxt,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { if (isSearching) isSearching = false else onKeyClick("ABC") }
                )
                Spacer(Modifier.width(8.dp))

                if (isSearching) {
                    Text(text = "Search emoji", color = ToolTxt, fontSize = 16.sp)
                } else {
                    // Compact search pill, then the category tabs share the rest of the row.
                    Row(
                        modifier = Modifier
                            .width(96.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(KeySpec)
                            .clickable { isSearching = true }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = ToolTxt, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Search", color = ToolTxt.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(4.dp))

                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            EmojiTab(
                                icon       = Icons.Default.AccessTime,
                                description = "Recently used",
                                isSelected = activeCategory == -1,
                                onClick    = {
                                    scope.launch { gridState.scrollToItem(categoryStartIndices[-1] ?: 0) }
                                }
                            )
                        }
                        itemsIndexed(EMOJI_CATEGORY_ICONS) { idx, icon ->
                            EmojiTab(
                                icon       = icon,
                                description = EMOJI_CATEGORY_NAMES.getOrElse(idx) { "Category" },
                                isSelected = activeCategory == idx,
                                onClick    = {
                                    scope.launch { gridState.scrollToItem(categoryStartIndices[idx] ?: 0) }
                                }
                            )
                        }
                    }
                }
            }

            // Google shows the recent-emoji grid as the default suggestion set
            // when the search field is empty, instead of a blank hint.
            val searchSuggestions: List<String>? =
                if (isSearching && searchQuery.isBlank()) recentEmojisSnapshot else searchResults

            // ── Emoji grid ────────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (isSearching && searchQuery.isBlank() && searchSuggestions.isNullOrEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Search for an emoji", color = ToolTxt, fontSize = 14.sp)
                    }
                } else if (isSearching && searchQuery.isNotBlank() && searchSuggestions?.isEmpty() == true) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching emoji found", color = ToolTxt, fontSize = 14.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        state          = gridState,
                        columns        = GridCells.Fixed(9),
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        val haptic = viewModel.hapticEnabled.value
                        if (isSearching && searchSuggestions != null) {
                            items(searchSuggestions, key = { it }) { emoji ->
                                EmojiCell(emoji, haptic,
                                    onClick = { handleEmojiClick(it) },
                                    onLongPress = { longPressedEmoji = it }
                                )
                            }
                        } else {
                            // Recently Used Section
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(9) }) {
                                SectionHeader("Recent emoji")
                            }
                            if (recentEmojisSnapshot.isEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(9) }) {
                                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                                        Text("No recent emoji", color = ToolTxt.copy(alpha = 0.5f), fontSize = 12.sp)
                                    }
                                }
                            } else {
                                items(recentEmojisSnapshot, key = { "recent_$it" }) { emoji ->
                                    EmojiCell(emoji, haptic,
                                        onClick = { handleEmojiClick(it) },
                                        onLongPress = { longPressedEmoji = it }
                                    )
                                }
                            }

                            // Categories Sections
                            EMOJI_CATEGORIES.forEachIndexed { catIdx, pair ->
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(9) }) {
                                    SectionHeader(EMOJI_CATEGORY_NAMES.getOrElse(catIdx) { pair.first })
                                }
                                items(pair.second, key = { "cat${catIdx}_$it" }) { emoji ->
                                    EmojiCell(emoji, haptic,
                                        onClick = { handleEmojiClick(it) },
                                        onLongPress = { longPressedEmoji = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isSearching) {
                // ── Live search field ───────────────────────────────────────────
                // Gboard places the actual editable box down here, just above the
                // keyboard, not in the header — the header stays a static title.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(KeySpec)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = ToolTxt, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Search", color = ToolTxt.copy(alpha = 0.5f), fontSize = 14.sp)
                            }
                            Text(searchQuery, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = ToolTxt,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                // ── Search keyboard ────────────────────────────────────────────
                // Reuses the real English qwerty rows (Shift + long-press accents
                // come along for free) instead of hand-rolling a second layout;
                // only the bottom row is bespoke, since "exit search" has no
                // equivalent on the main keyboard.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KbBg)
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KeyboardRows(
                        rows       = EnglishLayout(
                            isShifted    = viewModel.isShifted.value || viewModel.isCapsLock.value,
                            isEmailField = false
                        ).rows().dropLast(1), // drop the SYM/EMOJI_SWITCH/ENTER row — not meaningful while searching
                        mode       = KeyboardMode.ENGLISH,
                        isCapsLock = viewModel.isCapsLock.value,
                        viewModel  = viewModel,
                        showHints  = true,
                        onKeyClick = { key ->
                            when (key) {
                                "SHIFT"     -> viewModel.toggleShift()
                                "BACKSPACE" -> searchQuery = searchQuery.dropLast(1)
                                else -> {
                                    searchQuery += key
                                    viewModel.autoResetShift()
                                }
                            }
                        }
                    )
                    // Bottom space bar row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KeyButton(
                            key       = "ABC",
                            modifier  = Modifier.weight(1.3f).height(42.dp),
                            mode      = KeyboardMode.EMOJI,
                            isCapsLock = false,
                            viewModel = viewModel,
                            onClick   = { isSearching = false }
                        )
                        KeyButton(
                            key       = " ",
                            modifier  = Modifier.weight(3.6f).height(42.dp),
                            mode      = KeyboardMode.ENGLISH,
                            isCapsLock = false,
                            viewModel = viewModel,
                            onClick   = { searchQuery += " " }
                        )
                        KeyButton(
                            key       = ".",
                            modifier  = Modifier.weight(0.9f).height(42.dp),
                            mode      = KeyboardMode.ENGLISH,
                            isCapsLock = false,
                            viewModel = viewModel,
                            onClick   = { searchQuery += "." }
                        )
                        // Gboard-style round blue "go" button instead of a plain DONE label
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(AccentBlue)
                                .clickable { isSearching = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Done",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                // ── Gboard Style Bottom Action Bar ────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(KbBg)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    KeyButton(
                        key       = "ABC",
                        modifier  = Modifier.width(52.dp).height(40.dp),
                        mode      = KeyboardMode.EMOJI,
                        isCapsLock = false,
                        viewModel = viewModel,
                        onClick   = { onKeyClick("ABC") }
                    )
                    
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Emoji (active)
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AccentBlue)
                                .clickable { /* already in emoji */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.EmojiEmotions, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        }
                        
                        Spacer(Modifier.width(8.dp))
                        
                        // GIF
                        Text(
                            text = "GIF",
                            color = ToolTxt,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .clickable { onKeyClick("GIF_SWITCH") }
                        )

                        Spacer(Modifier.width(8.dp))
                        
                        // Stickers (Placeholder)
                        Icon(
                            imageVector = Icons.Default.StickyNote2,
                            contentDescription = "Stickers",
                            tint = ToolTxt,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { /* stickers */ }
                        )

                        Spacer(Modifier.width(12.dp))

                        // Emoticons (Placeholder)
                        Text(
                            text = ":-)",
                            color = ToolTxt,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { /* emoticons */ }
                        )
                    }

                    KeyButton(
                        key       = "BACKSPACE",
                        modifier  = Modifier.width(52.dp).height(40.dp),
                        mode      = KeyboardMode.EMOJI,
                        isCapsLock = false,
                        viewModel = viewModel,
                        onClick   = { onKeyClick("BACKSPACE") }
                    )
                }
            }
        }

        // Skin Tone Selector Popup
        longPressedEmoji?.let { emoji ->
            SkinTonePopup(
                baseEmoji = emoji,
                onSelect  = { handleEmojiClick(it) },
                onDismiss = { longPressedEmoji = null }
            )
        }
    }
}

/** Category names, in the same order as [EMOJI_CATEGORY_ICONS]. */
private val EMOJI_CATEGORY_NAMES = listOf(
    "Smileys & emotion", "People & body", "Animals & nature", "Food & drink",
    "Activities", "Travel & places", "Objects", "Symbols"
)

/** A single category icon tab in the emoji panel. */
@Composable
private fun EmojiTab(icon: ImageVector, description: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .width(if (isSelected) 48.dp else 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) AccentBlue else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = description,
            tint               = if (isSelected) Color.Black else ToolTxt,
            modifier           = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text          = title,
        color         = ToolTxt,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Bold,
        modifier      = Modifier
            .fillMaxWidth()
            .background(KbBg)
            .padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(
    emoji: String, 
    hapticEnabled: Boolean, 
    onClick: (String) -> Unit,
    onLongPress: (String) -> Unit
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = {
                    if (hapticEnabled) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    onClick(emoji)
                },
                onLongClick = {
                    if (hapticEnabled) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    }
                    onLongPress(emoji)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 36.sp)
    }
}

@Composable
private fun SkinTonePopup(baseEmoji: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    // Fitzpatrick modifiers
    val modifiers = listOf("", "\uD83C\uDFFB", "\uD83C\uDFFC", "\uD83C\uDFFD", "\uD83C\uDFFE", "\uD83C\uDFFF")
    val variants = modifiers.map { baseEmoji + it }
    
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 120.dp)
                .background(KbBg, RoundedCornerShape(8.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            variants.forEach { variant ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(KeySpec)
                        .clickable { onSelect(variant) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(variant, fontSize = 32.sp)
                }
            }
        }
    }
}
