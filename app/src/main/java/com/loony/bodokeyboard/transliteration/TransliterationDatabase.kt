package com.loony.bodokeyboard.transliteration

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite store for user-learned transliteration rules.
 *
 * Schema: learned_rules (id, latin, bodo, usage_count)
 * Unique constraint on (latin, bodo) — upsert on conflict.
 *
 * Rules with usage_count >= 3 are treated as "stable" and fed into the
 * engine as custom overrides so they take priority over the static mappings.
 */
class TransliterationDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME    = "transliteration.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME       = "learned_rules"
        private const val COLUMN_ID        = "id"
        private const val COLUMN_LATIN     = "latin"
        private const val COLUMN_BODO      = "bodo"
        private const val COLUMN_USAGE_COUNT = "usage_count"
    }

    data class LearnedRule(val id: Int, val latin: String, val bodo: String, val count: Int)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_NAME (" +
            "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "$COLUMN_LATIN TEXT, " +
            "$COLUMN_BODO TEXT, " +
            "$COLUMN_USAGE_COUNT INTEGER, " +
            "UNIQUE($COLUMN_LATIN, $COLUMN_BODO))"
        )
        seedInitialRules(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No destructive migrations: never drop learned_rules, or every future
        // version bump would silently wipe all user-learned corrections.
        // Add real ALTER TABLE migrations here as the schema evolves.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_NAME (" +
            "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "$COLUMN_LATIN TEXT, " +
            "$COLUMN_BODO TEXT, " +
            "$COLUMN_USAGE_COUNT INTEGER, " +
            "UNIQUE($COLUMN_LATIN, $COLUMN_BODO))"
        )
    }

    /** Populate the database with high-frequency Bodo words on first install. */
    private fun seedInitialRules(db: SQLiteDatabase) {
        val rules = arrayOf(
            // --- Pronouns & Demonstratives ---
            arrayOf("aang", "आं", "15"),
            arrayOf("nwng", "नों", "18"),
            arrayOf("bwi", "बै", "14"),
            arrayOf("biyw", "बियो", "16"),
            arrayOf("jwng", "जों", "12"),
            arrayOf("nwngswr", "नोंसोर", "15"),
            arrayOf("biswr", "बिसोर", "15"),
            arrayOf("be", "बे", "20"),
            // --- High-Frequency Verbs ---
            arrayOf("za", "जा", "20"),
            arrayOf("thang", "थां", "15"),
            arrayOf("phai", "फाय", "15"),
            arrayOf("mwn", "मोन", "18"),
            arrayOf("hwn", "हुन", "14"),
            arrayOf("gwdan", "गुदान", "10"),
            arrayOf("rao", "राव", "13"),
            arrayOf("labw", "लाबों", "16"),
            arrayOf("lang", "लां", "14"),
            arrayOf("din", "दिन", "12"),
            arrayOf("gono", "गनो", "10"),
            // --- Interrogatives & Adverbs ---
            arrayOf("swr", "सोर", "10"),
            arrayOf("ma", "मा", "22"),
            arrayOf("bwbha", "बब्हा", "12"),
            arrayOf("mabwla", "माब्ला", "15"),
            arrayOf("borai", "बराय", "11"),
            arrayOf("bidi", "बिदि", "13"),
            arrayOf("danw", "दानो", "14"),
            arrayOf("swng", "सों", "12"),
            // --- Nouns & Environment ---
            arrayOf("bodo", "बर'", "10"),
            arrayOf("dwisri", "दुइस्रि", "10"),
            arrayOf("mansi", "मानसि", "19"),
            arrayOf("gami", "गामि", "14"),
            arrayOf("phang", "फां", "13"),
            arrayOf("hazw", "हाजो", "15"),
            arrayOf("dwi", "दै", "20"),
            arrayOf("or", "अर'", "12"),
            arrayOf("bar", "बार", "15"),
            arrayOf("okhrang", "अख्रां", "14"),
            arrayOf("san", "सान", "16"),
            arrayOf("okhafwr", "अखाफोर", "13"),
            arrayOf("zau", "जाउ", "10"),
            // --- Conjunctions, Postpositions & Particles ---
            arrayOf("arw", "आरो", "22"),
            arrayOf("bla", "ब्ला", "17"),
            arrayOf("khow", "खौ", "25"),  // Accusative case marker
            arrayOf("ni", "नि", "24"),    // Genitive case marker
            arrayOf("on", "अन", "12"),
            arrayOf("nw", "नो", "20"),    // Emphatic particle
            arrayOf("ao", "आव", "22"),    // Locative case marker
            arrayOf("zwng", "जों", "18")  // Instrumental/Comitative marker
        )
        for (rule in rules) {
            db.execSQL(
                "INSERT INTO $TABLE_NAME ($COLUMN_LATIN, $COLUMN_BODO, $COLUMN_USAGE_COUNT) VALUES (?, ?, ?)",
                rule
            )
        }
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Record a successful transliteration choice.
     * Skip if the engine already produces this output by default (no point storing it).
     */
    fun learn(latin: String, bodo: String) {
        val engine = TransliterationEngine()
        if (engine.flush(latin) == bodo) return

        val query = """
            INSERT INTO $TABLE_NAME ($COLUMN_LATIN, $COLUMN_BODO, $COLUMN_USAGE_COUNT)
            VALUES (?, ?, 1)
            ON CONFLICT($COLUMN_LATIN, $COLUMN_BODO)
            DO UPDATE SET $COLUMN_USAGE_COUNT = $COLUMN_USAGE_COUNT + 1
        """.trimIndent()
        writableDatabase.execSQL(query, arrayOf(latin, bodo))
    }

    /** Erase all user-learned rules and restore the built-in seed data. */
    fun clearAll() {
        writableDatabase.delete(TABLE_NAME, null, null)
        seedInitialRules(writableDatabase)
    }

    fun deleteRule(id: Int) {
        writableDatabase.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun updateRule(id: Int, latin: String, bodo: String) {
        val values = ContentValues().apply {
            put(COLUMN_LATIN, latin)
            put(COLUMN_BODO, bodo)
        }
        writableDatabase.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /** Top-5 learned rules whose latin key starts with [prefix], ranked by usage. */
    fun getLearnedRules(prefix: String): List<Pair<String, String>> {
        val rules = mutableListOf<Pair<String, String>>()
        val cursor = readableDatabase.query(
            TABLE_NAME,
            arrayOf(COLUMN_LATIN, COLUMN_BODO),
            "$COLUMN_LATIN LIKE ?",
            arrayOf("$prefix%"),
            null, null,
            "$COLUMN_USAGE_COUNT DESC",
            "5"
        )
        cursor.use {
            if (it.moveToFirst()) do {
                rules.add(
                    it.getString(it.getColumnIndexOrThrow(COLUMN_LATIN)) to
                    it.getString(it.getColumnIndexOrThrow(COLUMN_BODO))
                )
            } while (it.moveToNext())
        }
        return rules
    }

    /** All learned rules, ranked by usage (used to initialise the engine). */
    fun getAllRules(): List<LearnedRule> {
        val rules = mutableListOf<LearnedRule>()
        val cursor = readableDatabase.query(
            TABLE_NAME, null, null, null, null, null, "$COLUMN_USAGE_COUNT DESC"
        )
        cursor.use {
            if (it.moveToFirst()) do {
                rules.add(LearnedRule(
                    id    = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                    latin = it.getString(it.getColumnIndexOrThrow(COLUMN_LATIN)),
                    bodo  = it.getString(it.getColumnIndexOrThrow(COLUMN_BODO)),
                    count = it.getInt(it.getColumnIndexOrThrow(COLUMN_USAGE_COUNT))
                ))
            } while (it.moveToNext())
        }
        return rules
    }

    /** Rules with usage_count >= 3 — high-confidence overrides for the engine. */
    fun getStableRules(): Map<String, String> {
        val rules = mutableMapOf<String, String>()
        val cursor = readableDatabase.query(
            TABLE_NAME,
            arrayOf(COLUMN_LATIN, COLUMN_BODO),
            "$COLUMN_USAGE_COUNT >= ?",
            arrayOf("3"),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) do {
                rules[it.getString(it.getColumnIndexOrThrow(COLUMN_LATIN))] =
                    it.getString(it.getColumnIndexOrThrow(COLUMN_BODO))
            } while (it.moveToNext())
        }
        return rules
    }
}
