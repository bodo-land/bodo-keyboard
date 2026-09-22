package com.loony.bodokeyboard.transliteration

/**
 * Bodo Devanagari Unicode constants — PramukhIME-compatible.
 * Source: pramukhime.com/help/bodo-typing-help
 * DOCUMENTED = explicitly stated on the help page
 * INFERRED   = Assamese chart + Devanagari conventions + Bodo phonology
 */
object U {
    // Independent vowels
    const val A   = "अ"  // DOCUMENTED (key: o — inherent/standalone)
    const val AA  = "आ"  // DOCUMENTED (key: a/A)
    const val I   = "इ"  // DOCUMENTED (key: i)
    const val II  = "ई"  // INFERRED   (key: ee)
    const val U_V = "उ"  // DOCUMENTED (key: u)
    const val UU  = "ऊ"  // INFERRED   (key: oo/U)
    const val RI  = "ऋ"  // INFERRED   (key: Ri)
    const val RII = "ॠ"  // INFERRED   (key: RI)
    const val E   = "ए"  // DOCUMENTED (key: e)
    const val AI  = "ऐ"  // DOCUMENTED (key: wi/ai)
    const val O   = "ओ"  // DOCUMENTED (key: w)
    const val AU  = "औ"  // DOCUMENTED (key: ou/wo)

    // Vowel signs (mātrās)
    const val M_AA  = "ा"  // DOCUMENTED
    const val M_I   = "ि"  // DOCUMENTED
    const val M_II  = "ी"  // INFERRED
    const val M_U   = "ु"  // DOCUMENTED
    const val M_UU  = "ू"  // INFERRED
    const val M_RI  = "ृ"  // INFERRED
    const val M_RII = "ॄ"  // INFERRED
    const val M_E   = "े"  // DOCUMENTED
    const val M_AI  = "ै"  // DOCUMENTED
    const val M_O   = "ो"  // DOCUMENTED
    const val M_AU  = "ौ"  // DOCUMENTED

    // Diacritics
    const val ANUSVARA = "ं"  // DOCUMENTED (key: ng/M)
    const val VISARGA  = "ः"  // INFERRED   (key: H)
    const val HALANT   = "्"  // derived — inserted between consonant clusters
    const val AVAGRAHA = "ऽ"  // DOCUMENTED (key: .a)

    // Consonants — Velars
    const val KA  = "क"  // INFERRED (key: ko — unaspirated)
    const val KHA = "ख"  // DOCUMENTED (key: k/kh — aspirated default)
    const val GA  = "ग"  // DOCUMENTED (key: g)
    const val GHA = "घ"  // INFERRED   (key: gh)
    const val NGA = "ङ"  // DOCUMENTED (key: NG)

    // Palatals
    const val CA  = "च"  // INFERRED (key: c)
    const val CHA = "छ"  // INFERRED (key: C/ch)
    const val JA  = "ज"  // DOCUMENTED (key: j)
    const val JHA = "झ"  // INFERRED   (key: jh/J)
    const val NYA = "ञ"  // INFERRED   (key: NY)

    // Retroflexes
    const val TTA  = "ट"  // INFERRED (key: T)
    const val TTHA = "ठ"  // INFERRED (key: Th)
    const val DDA  = "ड"  // INFERRED (key: D)
    const val DDHA = "ढ"  // INFERRED (key: Dh)
    const val NNA  = "ण"  // INFERRED (key: N)

    // Dentals
    const val TA  = "त"  // INFERRED   (key: to — unaspirated)
    const val THA = "थ"  // DOCUMENTED (key: t/th — aspirated default)
    const val DA  = "द"  // DOCUMENTED (key: d)
    const val DHA = "ध"  // DOCUMENTED (key: dh)
    const val NA  = "न"  // DOCUMENTED (key: n)

    // Labials
    const val PA  = "प"  // INFERRED   (key: po — unaspirated)
    const val PHA = "फ"  // DOCUMENTED (key: p/ph/f — aspirated default)
    const val BA  = "ब"  // DOCUMENTED (key: b)
    const val BHA = "भ"  // INFERRED   (key: bh/B)
    const val MA  = "म"  // DOCUMENTED (key: m)

    // Semivowels / liquids / sibilants
    const val YA  = "य"  // DOCUMENTED (key: I/y — capital-I or lowercase-y)
    const val RA  = "र"  // DOCUMENTED (key: r)
    const val LA  = "ल"  // DOCUMENTED (key: l)
    const val LLA = "ळ"  // INFERRED   (key: L)
    const val VA  = "व"  // DOCUMENTED (key: O — capital-O)
    const val SHA = "श"  // INFERRED   (key: S/sh/xh)
    const val SSA = "ष"  // INFERRED   (key: x/Xh)
    const val SA  = "स"  // DOCUMENTED (key: s)
    const val HA  = "ह"  // DOCUMENTED (key: h)

    // Special Bodo character
    const val MODIFIER_APOSTROPHE = "ʼ"  // DOCUMENTED (key: ')

    // Punctuation / symbols
    const val DANDA        = "।"   // DOCUMENTED (key: |)
    const val DOUBLE_DANDA = "॥"   // DOCUMENTED (key: ||)
    const val OM           = "ॐ"   // DOCUMENTED (key: OM)
    const val RUPEE        = "₹"   // DOCUMENTED (key: Rs)
    const val SWASTIKA     = "卍"   // DOCUMENTED (key: +-)
}

object BodoTranslitMappings {

    data class VowelEntry(
        /** Independent vowel form — used at word-start or after another vowel. */
        val standalone: String,
        /** Mātrā form — used after a consonant; empty string = inherent vowel (no sign added). */
        val matra: String,
    )

    /**
     * Vowel key mappings — ordered longest-first so the trie tokenizer picks
     * the longest match (e.g. "wi" before "w", "ou" before "o").
     *
     * NOTE: 'ng' and 'M' are listed here but handled by a special rule in the
     * engine: after a consonant/matra they emit anusvara; if followed by a vowel
     * they emit anusvara + ग + that vowel's matra.
     */
    val VOWELS: Map<String, VowelEntry> = linkedMapOf(
        // 3-char sequences
        "oM"  to VowelEntry(U.A + U.ANUSVARA, U.ANUSVARA),
        // 2-char sequences (longest first to beat single-char alternatives)
        "oo"  to VowelEntry(U.UU,       U.M_UU),   // oo before o
        "ou"  to VowelEntry(U.AU,       U.M_AU),   // ou before o
        "wo"  to VowelEntry(U.AU,       U.M_AU),   // wo before w
        "wi"  to VowelEntry(U.AI,       U.M_AI),   // wi before w
        "ai"  to VowelEntry(U.AI,       U.M_AI),   // ai before a
        "ee"  to VowelEntry(U.II,       U.M_II),   // ee before e
        "Ri"  to VowelEntry(U.RI,       U.M_RI),
        "RI"  to VowelEntry(U.RII,      U.M_RII),
        "ng"  to VowelEntry(U.ANUSVARA, U.ANUSVARA), // special ng-rule in engine
        // 1-char sequences
        "a"   to VowelEntry(U.AA,       U.M_AA),
        "A"   to VowelEntry(U.AA,       U.M_AA),
        "i"   to VowelEntry(U.I,        U.M_I),
        "u"   to VowelEntry(U.U_V,      U.M_U),
        "U"   to VowelEntry(U.UU,       U.M_UU),
        "e"   to VowelEntry(U.E,        U.M_E),
        "w"   to VowelEntry(U.O,        U.M_O),
        "o"   to VowelEntry(U.A,        ""),        // inherent vowel — no matra added
        "M"   to VowelEntry(U.ANUSVARA, U.ANUSVARA),
    )

    /**
     * Consonant key mappings — ordered longest-first.
     *
     * Bodo phonology defaults: k→ข (aspirated KHA), t→थ (aspirated THA),
     * p→फ (aspirated PHA). Unaspirated variants use the 'o'-suffix convention:
     * ko=क, to=त, po=प (see UNASPIRATED below).
     */
    val CONSONANTS: Map<String, String> = linkedMapOf(
        // 3-char sequences
        "khy" to U.KHA + U.HALANT + U.SSA,  // conjunct kṣ
        // 2-char sequences
        "NG"  to U.NGA,
        "NY"  to U.NYA,
        "kh"  to U.KHA,
        "gh"  to U.GHA,
        "ch"  to U.CHA,
        "jh"  to U.JHA,
        "Th"  to U.TTHA,
        "Dh"  to U.DDHA,
        "th"  to U.THA,
        "dh"  to U.DHA,
        "ph"  to U.PHA,
        "bh"  to U.BHA,
        "sh"  to U.SHA,
        "xh"  to U.SHA,
        "Xh"  to U.SSA,
        // 1-char sequences
        "k"   to U.KHA,   // aspirated default
        "g"   to U.GA,
        "c"   to U.CA,
        "C"   to U.CHA,
        "j"   to U.JA,
        "J"   to U.JHA,
        "T"   to U.TTA,
        "D"   to U.DDA,
        "N"   to U.NNA,
        "t"   to U.THA,   // aspirated default
        "d"   to U.DA,
        "n"   to U.NA,
        "p"   to U.PHA,   // aspirated default
        "f"   to U.PHA,
        "b"   to U.BA,
        "B"   to U.BHA,
        "m"   to U.MA,
        "y"   to U.YA,
        "I"   to U.YA,    // capital-I = य (DOCUMENTED)
        "r"   to U.RA,
        "l"   to U.LA,
        "L"   to U.LLA,
        "O"   to U.VA,    // capital-O = व (DOCUMENTED)
        "v"   to U.VA,
        "S"   to U.SHA,
        "x"   to U.SSA,
        "s"   to U.SA,
        "h"   to U.HA,
    )

    /**
     * Unaspirated base consonants typed via 'o'-suffix convention.
     * ko=क, to=त, po=प.
     *
     * These are NOT inserted into the trie; the engine handles them explicitly
     * to avoid 'ko' greedily consuming the 'ou' vowel sequence.
     */
    val UNASPIRATED: Map<String, String> = mapOf(
        "ko" to U.KA,
        "to" to U.TA,
        "po" to U.PA,
    )

    /**
     * Special / symbol mappings — ordered longest-first ('||' before '|').
     */
    val SPECIALS: Map<String, String> = linkedMapOf(
        "||" to U.DOUBLE_DANDA,
        "+-" to U.SWASTIKA,
        ".a" to U.AVAGRAHA,
        "Rs" to U.RUPEE,
        "OM" to U.OM,
        "|"  to U.DANDA,
        "'"  to U.MODIFIER_APOSTROPHE,
        "_"  to "", // Translit reset key
        "H"  to U.VISARGA,
    )

    /** Suggestion-bar alternates shown alongside the primary transliteration. */
    val LINGUISTIC_ALTERNATES: Map<String, String> = mapOf(
        U.ANUSVARA to U.NGA,  // anusvara ↔ full ङ
    )
}
