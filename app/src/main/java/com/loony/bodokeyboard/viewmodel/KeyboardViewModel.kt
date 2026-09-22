package com.loony.bodokeyboard.viewmodel

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.loony.bodokeyboard.data.KeyboardMode
import com.loony.bodokeyboard.data.GifResult
import com.loony.bodokeyboard.data.GiphyResponse
import com.loony.bodokeyboard.transliteration.BodoTranslitMappings
import com.loony.bodokeyboard.transliteration.TransliterationDatabase
import com.loony.bodokeyboard.transliteration.TransliterationEngine
import com.loony.bodokeyboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import androidx.lifecycle.viewModelScope
import java.io.File
import androidx.core.content.FileProvider

/**
 * Holds all keyboard state and drives UI recomposition via Compose State.
 */
class KeyboardViewModel : ViewModel() {

    private var db: TransliterationDatabase? = null

    fun initDatabase(context: Context) {
        if (db != null) return
        db = TransliterationDatabase(context)
        // Opening/seeding SQLite is disk I/O; keep it off the main thread.
        viewModelScope.launch(Dispatchers.IO) { refreshEngineRules() }
    }

    /** Only "stable" (usage_count >= 3) rules become engine overrides — a single
     *  mis-tap shouldn't permanently hijack a word's default transliteration. */
    private fun refreshEngineRules() {
        val stableRules = db?.getStableRules() ?: emptyMap()
        translitEngine.updateRules(stableRules)
    }

    // ── Layout state ──────────────────────────────────────────────────────────

    private val _isShifted  = mutableStateOf(false)
    val isShifted:  State<Boolean> = _isShifted

    private val _isCapsLock = mutableStateOf(false)
    val isCapsLock: State<Boolean> = _isCapsLock

    private val _isSymbols  = mutableStateOf(false)
    val isSymbols:  State<Boolean> = _isSymbols

    private val _isSymbols2 = mutableStateOf(false)
    val isSymbols2: State<Boolean> = _isSymbols2

    private val _keyboardMode = mutableStateOf(KeyboardMode.ENGLISH)
    val keyboardMode: State<KeyboardMode> = _keyboardMode
    private var lastTextMode = KeyboardMode.ENGLISH

    // ── Emoji panel ───────────────────────────────────────────────────────────

    val recentEmojis = mutableStateListOf<String>()
    private var prefs: android.content.SharedPreferences? = null

    fun initEmojiPrefs(context: Context) {
        prefs = context.getSharedPreferences("emoji_prefs", Context.MODE_PRIVATE)
        val saved = prefs?.getString("recent_emojis", "") ?: ""
        if (saved.isNotEmpty()) {
            recentEmojis.clear()
            recentEmojis.addAll(saved.split(","))
        }
    }

    fun addRecentEmoji(emoji: String) {
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        if (recentEmojis.size > 35) recentEmojis.removeAt(recentEmojis.size - 1)
        
        prefs?.edit()?.putString("recent_emojis", recentEmojis.joinToString(","))?.apply()
    }

    // ── GIF panel ─────────────────────────────────────────────────────────────

    private val _gifs = mutableStateOf<List<GifResult>>(emptyList())
    val gifs: State<List<GifResult>> = _gifs

    private val _isGifLoading = mutableStateOf(false)
    val isGifLoading: State<Boolean> = _isGifLoading

    private val _gifError = mutableStateOf(false)
    val gifError: State<Boolean> = _gifError

    private var gifSearchJob: Job? = null
    private var lastGifQuery: String = ""

    private companion object {
        const val NETWORK_TIMEOUT_MS = 10_000
    }

    fun searchGifs(query: String) {
        lastGifQuery = query
        gifSearchJob?.cancel()

        gifSearchJob = viewModelScope.launch(Dispatchers.IO) {
            if (query.isNotEmpty()) {
                delay(500) // Debounce for search
            }
            _isGifLoading.value = true
            _gifError.value = false
            try {
                val apiKey = BuildConfig.GIPHY_API_KEY
                val urlString = if (query.isEmpty()) {
                    "https://api.giphy.com/v1/gifs/trending?api_key=$apiKey&limit=20&rating=g"
                } else {
                    "https://api.giphy.com/v1/gifs/search?api_key=$apiKey&q=$query&limit=20&rating=g"
                }
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = NETWORK_TIMEOUT_MS
                connection.readTimeout = NETWORK_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == 200) {
                    val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = Json { ignoreUnknownKeys = true }.decodeFromString<GiphyResponse>(jsonString)
                    _gifs.value = response.data.map {
                        GifResult(
                            id = it.id,
                            url = it.images.fixed_height.url,
                            previewUrl = it.images.preview_gif.url
                        )
                    }
                } else {
                    _gifError.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _gifError.value = true
            } finally {
                _isGifLoading.value = false
            }
        }
    }

    /** Retries the most recent search/trending fetch after a failure. */
    fun retryGifs() = searchGifs(lastGifQuery)

    /** Downloads a GIF to the cache directory and returns a content URI for sharing. */
    fun downloadGif(context: Context, gifUrl: String, onResult: (android.net.Uri?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "gifs").apply { mkdirs() }
                // Use ID or hash for filename to avoid collisions and invalid chars
                val fileName = java.util.UUID.nameUUIDFromBytes(gifUrl.toByteArray()).toString() + ".gif"
                val file = File(cacheDir, fileName)

                if (!file.exists()) {
                    val url = URL(gifUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = NETWORK_TIMEOUT_MS
                    connection.readTimeout = NETWORK_TIMEOUT_MS
                    connection.connect()
                    
                    if (connection.responseCode == 200) {
                        connection.inputStream.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    withContext(Dispatchers.Main) { onResult(uri) }
                } else {
                    withContext(Dispatchers.Main) { onResult(null) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    // ── Transliteration ───────────────────────────────────────────────────────

    val translitEngine: TransliterationEngine by lazy { TransliterationEngine() }

    private val _translitBuffer = mutableStateOf("")
    val translitBuffer: State<String> = _translitBuffer

    fun isTranslitMode(): Boolean = _keyboardMode.value == KeyboardMode.TRANSLIT

    fun feedTranslit(char: Char): TransliterationEngine.Result {
        val result = translitEngine.feed(_translitBuffer.value, char)
        _translitBuffer.value = _translitBuffer.value + char
        return result
    }

    fun translitBackspace(): String {
        val newLatin = translitEngine.backspace(_translitBuffer.value)
        _translitBuffer.value = newLatin
        return translitEngine.flush(newLatin)
    }

    fun flushTranslit(): String {
        val committed = translitEngine.flush(_translitBuffer.value)
        _translitBuffer.value = ""
        return committed
    }

    fun clearTranslitBuffer() {
        _translitBuffer.value = ""
    }

    // ── Preferences ───────────────────────────────────────────────────────────

    val hapticEnabled            = mutableStateOf(true)
    val soundEnabled             = mutableStateOf(false)
    val keyboardHeightMultiplier = mutableStateOf(1.0f)
    val suggestionsEnabled       = mutableStateOf(true)

    // ── Editor info ───────────────────────────────────────────────────────────

    private val _imeAction    = mutableStateOf(EditorInfo.IME_ACTION_DONE)
    val imeAction: State<Int> = _imeAction

    private val _isEmailField = mutableStateOf(false)
    val isEmailField: State<Boolean> = _isEmailField

    private var _editorInfo: EditorInfo? = null
    val editorInfo: EditorInfo? get() = _editorInfo

    fun updateEditorInfo(info: EditorInfo?) {
        _editorInfo = info
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        _imeAction.value = if (action == EditorInfo.IME_ACTION_UNSPECIFIED)
            EditorInfo.IME_ACTION_DONE else action

        val inputType = info?.inputType ?: 0
        _isEmailField.value =
            (inputType and EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0 ||
            (inputType and EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) != 0

        val baseType = inputType and EditorInfo.TYPE_MASK_CLASS
        if (baseType == EditorInfo.TYPE_CLASS_NUMBER || baseType == EditorInfo.TYPE_CLASS_PHONE) {
            _keyboardMode.value = KeyboardMode.NUMERIC
        } else if (_keyboardMode.value == KeyboardMode.NUMERIC) {
            _keyboardMode.value = lastTextMode
        }
    }

    // ── Shift / CapsLock ──────────────────────────────────────────────────────

    private var lastShiftTime = 0L

    fun toggleShift() {
        val now = System.currentTimeMillis()
        when {
            _isCapsLock.value -> {
                _isCapsLock.value = false
                _isShifted.value  = false
            }
            _isShifted.value && (now - lastShiftTime) < 400L -> {
                _isCapsLock.value = true
            }
            else -> _isShifted.value = !_isShifted.value
        }
        lastShiftTime = now
    }

    fun setShift(shifted: Boolean) {
        _isShifted.value = shifted
    }

    fun autoResetShift() {
        if (_isShifted.value && !_isCapsLock.value) _isShifted.value = false
    }

    // ── Symbols / Mode ────────────────────────────────────────────────────────

    fun toggleSymbols() {
        if (_isSymbols.value) {
            resetSymbols()
        } else {
            _isSymbols.value = true
            _isSymbols2.value = false
        }
    }

    fun toggleSymbolsPage() {
        _isSymbols2.value = !_isSymbols2.value
    }

    fun resetSymbols() {
        _isSymbols.value = false
        _isSymbols2.value = false
    }

    fun setMode(mode: KeyboardMode) {
        _keyboardMode.value   = mode
        if (mode == KeyboardMode.BODO || mode == KeyboardMode.ENGLISH || mode == KeyboardMode.TRANSLIT) {
            lastTextMode = mode
        }
        _isSymbols.value      = false
        _isSymbols2.value     = false
        _isShifted.value      = false
        _isCapsLock.value     = false
        _translitBuffer.value = ""
    }

    // ── Word lists ────────────────────────────────────────────────────────────

    private var bodoWordList: List<String> = listOf(
        "बड'", "बर'", "बडलेण्ड", "बिथ'राय", "गोजोन्थों", "खालाम",
        "गोजोन", "नोगोर", "माव", "मोजां", "जोबोर", "हाबा",
        "थां", "आस", "बिबार", "गोलाव", "दाउ", "फिसाज",
        "गेलेब", "आलो", "बाथ्रा", "सोरां", "मानसि", "नैथि"
    )

    private var englishWordList: List<String> = listOf(
        "Bodo", "Keyboard", "English", "Hello", "World",
        "Good", "Thank", "Please", "Sorry", "Welcome"
    )

    fun setWordLists(bodo: List<String>, english: List<String>) {
        if (bodo.isNotEmpty())    bodoWordList    = bodo
        if (english.isNotEmpty()) englishWordList = english
        updateSuggestions("")
    }

    // ── Suggestions ───────────────────────────────────────────────────────────

    private val _suggestions   = mutableStateOf(listOf<String>())
    val suggestions: State<List<String>> = _suggestions

    private var suggestionsJob: Job? = null

    fun updateSuggestions(text: String) {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            if (text.isNotEmpty()) delay(50)

            if (!suggestionsEnabled.value) {
                _suggestions.value = emptyList()
                return@launch
            }

            val mode = _keyboardMode.value
            if (text.isEmpty()) {
                _suggestions.value = bodoWordList.take(3)
                return@launch
            }

            val result = withContext(Dispatchers.Default) {
                val suggestionsList = mutableListOf<String>()
                val isLatinInput = (mode == KeyboardMode.ENGLISH || mode == KeyboardMode.TRANSLIT) &&
                                   !text.any { it in 'ऀ'..'ॿ' }

                if (isLatinInput) {
                    suggestionsList.add(text)
                    val learned = withContext(Dispatchers.IO) {
                        db?.getLearnedRules(text.lowercase()) ?: emptyList()
                    }
                    learned.forEach { suggestionsList.add(it.second) }

                    val translitPrefix = translitEngine.flush(text.lowercase())
                    if (translitPrefix != text) {
                        suggestionsList.add(translitPrefix)
                        BodoTranslitMappings.LINGUISTIC_ALTERNATES.forEach { (primary, alt) ->
                            if (translitPrefix.endsWith(primary)) {
                                suggestionsList.add(
                                    translitPrefix.substring(0, translitPrefix.length - primary.length) + alt
                                )
                            }
                        }
                    }
                    val completions = bodoWordList.filter {
                        it.startsWith(translitPrefix) && it != translitPrefix
                    }.take(3)
                    suggestionsList.addAll(completions)
                } else {
                    val filtered = bodoWordList.filter { it.startsWith(text) }.take(3)
                    suggestionsList.addAll(filtered)
                    if (suggestionsList.isEmpty()) suggestionsList.addAll(bodoWordList.take(3))
                }

                suggestionsList.distinct().take(3)
            }

            _suggestions.value = result
        }
    }

    // ── Learning ──────────────────────────────────────────────────────────────

    fun clearLearnedWords() {
        viewModelScope.launch(Dispatchers.IO) {
            db?.clearAll()
            refreshEngineRules()
        }
    }

    fun learnTransliteration(latin: String, bodo: String) {
        if (latin.isEmpty() || bodo.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val components  = translitEngine.decompose(latin.lowercase())
            val defaultBodo = translitEngine.flush(latin.lowercase())

            if (defaultBodo == bodo) {
                components.forEach { (lPart, bPart) -> db?.learn(lPart, bPart) }
            } else {
                db?.learn(latin.lowercase(), bodo)
            }

            refreshEngineRules()
        }
    }
}
