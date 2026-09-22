package com.loony.bodokeyboard

import android.content.ClipboardManager
import android.content.SharedPreferences
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.loony.bodokeyboard.data.KeyboardMode
import com.loony.bodokeyboard.viewmodel.KeyboardViewModel
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

private val TRAILING_WORD_RE = Regex("""\S+$""")

class BodoIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle:           Lifecycle         get() = lifecycleRegistry
    override val viewModelStore:      ViewModelStore    get() = store
    override val savedStateRegistry:  SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val viewModel = KeyboardViewModel()
    private lateinit var prefs: SharedPreferences
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "haptic" -> viewModel.hapticEnabled.value             = p.getBoolean("haptic", true)
            "sound"  -> viewModel.soundEnabled.value              = p.getBoolean("sound", false)
            "height" -> viewModel.keyboardHeightMultiplier.value  = p.getFloat("height", 1.0f)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        viewModel.initDatabase(this)
        viewModel.initEmojiPrefs(this)
        prefs = getSharedPreferences("keyboard_settings", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        loadSettings()
        loadWordLists()
    }

    private fun loadSettings() {
        viewModel.hapticEnabled.value            = prefs.getBoolean("haptic", true)
        viewModel.soundEnabled.value             = prefs.getBoolean("sound", false)
        viewModel.keyboardHeightMultiplier.value = prefs.getFloat("height", 1.0f)
    }

    private fun loadWordLists() {
        viewModel.setWordLists(
            bodo    = readAsset("bodo_words.txt"),
            english = readAsset("english_words.txt")
        )
    }

    private fun readAsset(filename: String): List<String> = try {
        assets.open(filename).bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    } catch (_: Exception) { emptyList() }

    // ── View ──────────────────────────────────────────────────────────────────

    override fun onCreateInputView(): View {
        window?.window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            win.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KeyboardScreen(
                    viewModel   = viewModel,
                    onKeyClick  = { key -> handleKeyClick(key) },
                    onSuggestionClick = { word -> handleSuggestion(word) },
                    onSpaceDrag = { delta -> moveCursor(delta) }
                )
            }
        }
        root.addView(composeView)
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        window?.window?.decorView?.let { dv ->
            dv.setViewTreeLifecycleOwner(this)
            dv.setViewTreeViewModelStoreOwner(this)
            dv.setViewTreeSavedStateRegistryOwner(this)
        }
        return root
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        // Runs on every editor bind, even when the input view itself doesn't
        // toggle (e.g. focus moves between two fields in the same screen) —
        // onStartInputView is not guaranteed to re-fire in that case, so this
        // is the only reliable place to flush stale state from the old field.
        viewModel.updateEditorInfo(info)
        commitAndClearTranslit()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        commitAndClearTranslit()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        loadSettings()
        viewModel.updateEditorInfo(info)
        commitAndClearTranslit()
        checkAutoCap(currentInputConnection ?: return)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Flush pending translit buffer when leaving the field
        commitAndClearTranslit()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val ic = currentInputConnection ?: return
        if (viewModel.isTranslitMode()) {
            refreshTranslitSuggestions(ic)
            return
        }
        val textBefore = ic.getTextBeforeCursor(20, 0)?.toString() ?: ""
        val word = textBefore.split(" ", "\n").lastOrNull() ?: ""
        viewModel.updateSuggestions(word)
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!isFullscreenMode) outInsets.contentTopInsets = outInsets.visibleTopInsets
    }

    // ── Key handling ──────────────────────────────────────────────────────────

    private var lastSpaceTime = 0L

    private fun handleKeyClick(key: String) {
        val ic = currentInputConnection ?: return

        if (viewModel.soundEnabled.value) {
            (getSystemService(AUDIO_SERVICE) as AudioManager)
                .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }

        // Rich Content (GIF) handling
        if (key.startsWith("content://")) {
            handleGifCommit(android.net.Uri.parse(key))
            return
        }

        // GIF handling (fallback or legacy)
        if (key.startsWith("http")) {
            ic.commitText(key, 1)
            return
        }

        // Route all printable characters through transliteration when in TRANSLIT mode
        if (viewModel.isTranslitMode() && (key.length == 1 && key[0].isLetter() || key == "_")) {
            handleTranslitChar(key[0])
            checkAutoCap(ic)
            return
        }

        when (key) {
            "BACKSPACE" -> {
                if (viewModel.isTranslitMode() && viewModel.translitBuffer.value.isNotEmpty()) {
                    // Remove last Latin keystroke; show the updated Devanagari as composing
                    val newDev = viewModel.translitBackspace()
                    ic.setComposingText(newDev, 1)
                } else {
                    ic.deleteSurroundingText(codeUnitsForBackspace(ic), 0)
                }
                checkAutoCap(ic)
            }

            "BACKSPACE_WORD" -> {
                if (viewModel.isTranslitMode() && viewModel.translitBuffer.value.isNotEmpty()) {
                    // Clear entire translit buffer first
                    viewModel.clearTranslitBuffer()
                    ic.setComposingText("", 1)
                } else {
                    val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: return
                    if (before.isEmpty()) return
                    val trimmed  = before.trimEnd()
                    val spaceIdx = trimmed.lastIndexOf(' ')
                    val deleteCount = if (spaceIdx == -1) trimmed.length + (before.length - trimmed.length)
                                      else before.length - spaceIdx - 1
                    ic.deleteSurroundingText(deleteCount.coerceAtLeast(1), 0)
                }
            }

            "SPACE" -> {
                val now = System.currentTimeMillis()
                // Flush any pending translit buffer first, whichever branch runs below —
                // otherwise a double-tap-to-period can replace the visible composing
                // text while the buffer still holds the stale Latin keystrokes.
                if (viewModel.isTranslitMode()) {
                    val flushed = viewModel.flushTranslit()
                    if (flushed.isNotEmpty()) ic.commitText(flushed, 1)
                }
                if (now - lastSpaceTime < 300L) {
                    // Double tap space -> insert period
                    val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
                    if (before.endsWith(" ") && before.length >= 2 && before[before.length-2].isLetterOrDigit()) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                    } else {
                        ic.commitText(" ", 1)
                    }
                } else {
                    ic.commitText(" ", 1)
                }
                lastSpaceTime = now
                viewModel.updateSuggestions("")
                checkAutoCap(ic)
            }

            "ENTER" -> {
                if (viewModel.isTranslitMode()) commitAndClearTranslit()
                
                val actionId = viewModel.imeAction.value
                val inputType = viewModel.editorInfo?.inputType ?: 0
                val isMultiLine = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0

                if (!isMultiLine && actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(actionId)
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
                
                viewModel.updateSuggestions("")
                checkAutoCap(ic)
            }

            "PASTE" -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!text.isNullOrEmpty()) ic.commitText(text, 1)
            }

            "SHIFT" -> viewModel.toggleShift()
            "SYM"   -> viewModel.toggleSymbols()
            "SYM_PAGE" -> viewModel.toggleSymbolsPage()

            "ABC" -> {
                if (viewModel.keyboardMode.value == KeyboardMode.EMOJI || 
                    viewModel.keyboardMode.value == KeyboardMode.GIF ||
                    viewModel.keyboardMode.value == KeyboardMode.NUMERIC) {
                    viewModel.setMode(KeyboardMode.ENGLISH)
                } else {
                    viewModel.resetSymbols()
                }
            }

            "MODE_SWITCH" -> {
                commitAndClearTranslit()
                viewModel.setMode(
                    if (viewModel.keyboardMode.value == KeyboardMode.BODO) KeyboardMode.ENGLISH
                    else KeyboardMode.BODO
                )
            }

            "SWITCH_BODO" -> {
                commitAndClearTranslit()
                viewModel.setMode(KeyboardMode.BODO)
            }

            "SWITCH_EN" -> {
                commitAndClearTranslit()
                viewModel.setMode(KeyboardMode.ENGLISH)
            }

            "TRANSLIT_TOGGLE" -> {
                commitAndClearTranslit()
                val next = if (viewModel.keyboardMode.value == KeyboardMode.TRANSLIT)
                    KeyboardMode.ENGLISH else KeyboardMode.TRANSLIT
                viewModel.setMode(next)
            }

            "EMOJI_SWITCH" -> {
                commitAndClearTranslit()
                viewModel.setMode(KeyboardMode.EMOJI)
            }

            "SUGGESTION_TOGGLE" -> {
                viewModel.suggestionsEnabled.value = !viewModel.suggestionsEnabled.value
                // Refresh suggestions based on new state
                val ic = currentInputConnection
                if (ic != null) {
                    val textBefore = ic.getTextBeforeCursor(20, 0)?.toString() ?: ""
                    val word = textBefore.split(" ", "\n").lastOrNull() ?: ""
                    viewModel.updateSuggestions(word)
                }
            }

            "GIF_SWITCH" -> {
                commitAndClearTranslit()
                viewModel.setMode(KeyboardMode.GIF)
            }

            "SETTINGS_OPEN" -> {
                val intent = android.content.Intent(this, MainActivity::class.java)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }

            "COLLAPSE" -> requestHideSelf(0)

            else -> {
                if (viewModel.isTranslitMode()) {
                    // Non-letter key in translit mode (punctuation, numbers):
                    // flush pending buffer then commit the key as-is
                    val flushed = viewModel.flushTranslit()
                    if (flushed.isNotEmpty()) ic.commitText(flushed, 1)
                    ic.commitText(key, 1)
                } else {
                    ic.commitText(key, 1)
                    viewModel.autoResetShift()
                }
                checkAutoCap(ic)
            }
        }
    }

    private fun handleGifCommit(uri: android.net.Uri) {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo ?: return

        // Check if the target app supports GIF
        val mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        val gifSupported = mimeTypes.any { it == "image/gif" }

        if (gifSupported) {
            val inputContentInfo = InputContentInfoCompat(
                uri,
                android.content.ClipDescription("GIF", arrayOf("image/gif")),
                null // linkUri
            )
            InputConnectionCompat.commitContent(
                ic,
                editorInfo,
                inputContentInfo,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null // opts
            )
        } else {
            android.widget.Toast.makeText(this, "This field doesn't support GIFs", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Number of UTF-16 code units making up the single grapheme cluster (emoji, ZWJ
     * sequence, surrogate pair, etc.) immediately before the cursor, so backspace
     * deletes one whole visible character instead of splitting it.
     */
    private fun codeUnitsForBackspace(ic: InputConnection): Int {
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return 1
        if (before.isEmpty()) return 1
        val boundary = BreakIterator.getCharacterInstance().apply { setText(before) }
        boundary.last()
        val clusterStart = boundary.previous()
        return if (clusterStart == BreakIterator.DONE) 1 else before.length - clusterStart
    }

    private fun checkAutoCap(ic: android.view.inputmethod.InputConnection) {
        val mode = viewModel.keyboardMode.value
        if (mode != KeyboardMode.ENGLISH && mode != KeyboardMode.TRANSLIT) return
        
        val before = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""
        val shouldCap = before.isEmpty() || 
                        before.endsWith(". ") || 
                        before.endsWith("! ") || 
                        before.endsWith("? ") ||
                        before.endsWith("\n")
        
        if (shouldCap && !viewModel.isShifted.value && !viewModel.isCapsLock.value) {
            viewModel.setShift(true)
        }
    }

    // ── Transliteration helpers ───────────────────────────────────────────────

    /**
     * Feed [char] through the engine, apply the result to the InputConnection:
     *   - Committed text → [commitText] (clears the composing region implicitly)
     *   - Pending text   → [setComposingText] (shown underlined in the field)
     */
    private fun handleTranslitChar(char: Char) {
        val ic = currentInputConnection ?: return
        // Case is significant: I→य, O→व, NG→ङ, Th→ठ, S→श, etc.
        val result = viewModel.feedTranslit(char)

        if (result.commit.isNotEmpty()) {
            ic.commitText(result.commit, 1)
            ic.setComposingText(result.pending, 1)
        } else {
            ic.setComposingText(result.pending, 1)
        }

        refreshTranslitSuggestions(ic)
        viewModel.autoResetShift()
    }

    /**
     * Update suggestions from the Devanagari text before the cursor.
     * With the new engine, composing text is already Devanagari, so
     * getTextBeforeCursor returns a pure Devanagari string — no stripping needed.
     */
    private fun refreshTranslitSuggestions(ic: android.view.inputmethod.InputConnection) {
        val raw  = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        val word = raw.split(" ", "\n").lastOrNull() ?: ""
        viewModel.updateSuggestions(word)
    }

    /**
     * Force-flush the translit buffer to the InputConnection and clear it.
     * Safe to call even when the buffer is empty or IC is null.
     */
    private fun commitAndClearTranslit() {
        if (viewModel.translitBuffer.value.isEmpty()) return
        val ic = currentInputConnection ?: run {
            viewModel.clearTranslitBuffer()
            return
        }
        val text = viewModel.flushTranslit()
        if (text.isNotEmpty()) ic.commitText(text, 1)
    }

    private fun handleSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val pendingLatin = viewModel.translitBuffer.value

        // If user picked a Bodo word while typing Latin, 'Train' the system
        if (pendingLatin.isNotEmpty()) {
            viewModel.learnTransliteration(pendingLatin, word)
        }

        // 1. Clear any pending translit state (Latin composing text)
        viewModel.clearTranslitBuffer()
        ic.setComposingText("", 1)

        // 2. Identify the word-part to replace (the word currently being typed)
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        // Matches any non-whitespace characters at the end of the string
        val lastWordMatch = TRAILING_WORD_RE.find(before)

        if (lastWordMatch != null) {
            ic.deleteSurroundingText(lastWordMatch.value.length, 0)
        }

        // 3. Commit the suggestion followed by a space
        ic.commitText("$word ", 1)
        
        // 4. Reset suggestions for the next word
        viewModel.updateSuggestions("")
    }

    // ── Cursor movement ───────────────────────────────────────────────────────

    private fun moveCursor(delta: Int) {
        val ic       = currentInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val newPos   = (extracted.selectionStart + delta).coerceIn(0, extracted.text.length)
        ic.setSelection(newPos, newPos)
    }
}
