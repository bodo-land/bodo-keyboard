package com.loony.bodokeyboard.transliteration

import java.text.Normalizer

/**
 * Bodo Roman→Devanagari transliteration engine.
 *
 * Architecture mirrors bodo-typewriter/src/engine:
 *
 *   Latin string
 *       ↓  tokenize()       — trie-based longest-match (O(n))
 *   List<Token>
 *       ↓  processTokens()  — three-state machine
 *   Devanagari Unicode (NFC)
 *
 * State machine
 * -------------
 *   INITIAL         — start of word / after space or passthrough
 *   AFTER_CONSONANT — consonant emitted; next vowel becomes mātrā
 *   AFTER_VOWEL     — vowel emitted; next vowel is standalone
 *
 * Halant insertion
 * ----------------
 *   Consonant immediately after consonant → U+094D HALANT inserted so the
 *   pair forms a conjunct cluster (e.g. "ks" → क्स).
 *
 * Inherent vowel ('o' key)
 * ------------------------
 *   'o' after consonant = inherent vowel; no sign is appended.
 *   'o' at word start / after vowel = standalone अ.
 *
 * 'ng' / 'M' rule  (DOCUMENTED)
 * -------------------------------
 *   After any content → anusvara ं.
 *   If followed by a vowel token → anusvara ं + ग + that vowel's mātrā.
 *
 * Unaspirated bases  (ko, to, po)
 * --------------------------------
 *   k/t/p + inherent-'o' → क/त/प instead of ख/थ/फ.
 *   NOT in the trie to avoid consuming 'ou' greedily; resolved via lookahead.
 *
 * Streaming interface (for BodoIME)
 * ----------------------------------
 *   feed(latin, char) → Result(pending = Devanagari composing text)
 *   The user sees Devanagari forming in real time, not Roman characters.
 *   flush(latin)      → final committed Devanagari string (SPACE / ENTER)
 *   backspace(latin)  → new Latin buffer after removing one keystroke
 */
class TransliterationEngine {

    // ── Token types ───────────────────────────────────────────────────────────

    enum class TokenKind { VOWEL, CONSONANT, SPECIAL, PASSTHROUGH }

    data class Token(val raw: String, val kind: TokenKind)

    // ── Result ────────────────────────────────────────────────────────────────

    /**
     * Returned by [feed].
     * [commit]  — always empty; the engine never commits mid-keystroke.
     * [pending] — Devanagari composing text; pass to setComposingText().
     */
    data class Result(val commit: String, val pending: String)

    // ── Trie ──────────────────────────────────────────────────────────────────

    companion object {
        private class TrieNode {
            val children = HashMap<Char, TrieNode>(4)
            var kind: TokenKind? = null
        }

        // Built once at first use, shared across all engine instances.
        private val trieRoot: TrieNode by lazy {
            val root = TrieNode()
            fun insert(key: String, kind: TokenKind) {
                var node = root
                for (ch in key) node = node.children.getOrPut(ch) { TrieNode() }
                node.kind = kind
            }
            BodoTranslitMappings.SPECIALS.keys.forEach   { insert(it, TokenKind.SPECIAL)   }
            BodoTranslitMappings.CONSONANTS.keys.forEach { insert(it, TokenKind.CONSONANT) }
            BodoTranslitMappings.VOWELS.keys.forEach     { insert(it, TokenKind.VOWEL)     }
            root
        }
    }

    // ── Tokenizer ─────────────────────────────────────────────────────────────

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            var node = trieRoot
            var lastKind: TokenKind? = null
            var lastEnd = i
            var j = i
            while (j < input.length) {
                node = node.children[input[j]] ?: break
                j++
                if (node.kind != null) { lastKind = node.kind; lastEnd = j }
            }
            if (lastKind != null) {
                tokens.add(Token(input.substring(i, lastEnd), lastKind))
                i = lastEnd
            } else {
                tokens.add(Token(input[i].toString(), TokenKind.PASSTHROUGH))
                i++
            }
        }
        return tokens
    }

    // ── State machine ─────────────────────────────────────────────────────────

    private enum class State { INITIAL, AFTER_CONSONANT, AFTER_VOWEL }

    fun transliterate(input: String): String {
        if (input.isEmpty()) return ""

        // Whole-word learned overrides take priority (e.g. "bodo" → "बर'")
        customRules[input]?.let { return it }

        val tokens = tokenize(input)
        val out = StringBuilder()
        var state = State.INITIAL
        var i = 0

        while (i < tokens.size) {
            val token = tokens[i]
            when (token.kind) {

                TokenKind.SPECIAL -> {
                    out.append(BodoTranslitMappings.SPECIALS[token.raw] ?: token.raw)
                    state = State.INITIAL
                    i++
                }

                TokenKind.PASSTHROUGH -> {
                    out.append(token.raw)
                    state = State.INITIAL
                    i++
                }

                TokenKind.VOWEL -> {
                    if (token.raw == "ng" || token.raw == "M") {
                        // DOCUMENTED ng/M rule
                        out.append(U.ANUSVARA)
                        val next = tokens.getOrNull(i + 1)
                        if (next?.kind == TokenKind.VOWEL) {
                            val m = BodoTranslitMappings.VOWELS[next.raw]?.matra ?: ""
                            out.append(U.GA)
                            if (m.isNotEmpty()) out.append(m)
                            i++ // consume the following vowel token
                        }
                        state = State.AFTER_VOWEL
                    } else {
                        val entry = BodoTranslitMappings.VOWELS[token.raw]
                        if (state == State.AFTER_CONSONANT) {
                            val matra = entry?.matra ?: ""
                            if (matra.isNotEmpty()) out.append(matra)
                            // matra == "" → inherent 'o'; consonant already in out
                        } else {
                            out.append(entry?.standalone ?: token.raw)
                        }
                        state = State.AFTER_VOWEL
                    }
                    i++
                }

                TokenKind.CONSONANT -> {
                    // Unaspirated-base rule: k/t/p + 'o' → क/त/प (not ख/थ/फ)
                    val next = tokens.getOrNull(i + 1)
                    val unaspKey = token.raw + "o"
                    val unaspChar = if (
                        next?.kind == TokenKind.VOWEL && next.raw == "o" &&
                        BodoTranslitMappings.UNASPIRATED.containsKey(unaspKey)
                    ) BodoTranslitMappings.UNASPIRATED[unaspKey] else null

                    val cChar = unaspChar ?: (BodoTranslitMappings.CONSONANTS[token.raw] ?: token.raw)
                    if (state == State.AFTER_CONSONANT) out.append(U.HALANT)
                    out.append(cChar)
                    state = if (unaspChar != null) {
                        i++ // consume the 'o' token
                        State.AFTER_VOWEL
                    } else {
                        State.AFTER_CONSONANT
                    }
                    i++
                }
            }
        }

        return Normalizer.normalize(out.toString(), Normalizer.Form.NFC)
    }

    // ── Custom / learned rules ────────────────────────────────────────────────

    // Replaced wholesale (never mutated in place) so concurrent reads from
    // transliterate() never observe a partially-cleared map.
    @Volatile
    private var customRules: Map<String, String> = emptyMap()

    fun updateRules(rules: Map<String, String>) {
        customRules = rules
    }

    // ── Streaming interface for BodoIME ───────────────────────────────────────

    fun feed(latinBuffer: String, newChar: Char): Result {
        val newLatin = latinBuffer + newChar
        return Result(commit = "", pending = transliterate(newLatin))
    }

    fun flush(latinBuffer: String): String = transliterate(latinBuffer)

    fun backspace(latinBuffer: String): String =
        if (latinBuffer.isEmpty()) "" else latinBuffer.dropLast(1)

    fun decompose(latinBuffer: String): List<Pair<String, String>> {
        if (latinBuffer.isEmpty()) return emptyList()
        return tokenize(latinBuffer).map { token ->
            val dev = when (token.kind) {
                TokenKind.CONSONANT   -> BodoTranslitMappings.CONSONANTS[token.raw] ?: token.raw
                TokenKind.VOWEL       -> BodoTranslitMappings.VOWELS[token.raw]?.standalone ?: token.raw
                TokenKind.SPECIAL     -> BodoTranslitMappings.SPECIALS[token.raw] ?: token.raw
                TokenKind.PASSTHROUGH -> token.raw
            }
            token.raw to dev
        }
    }
}
