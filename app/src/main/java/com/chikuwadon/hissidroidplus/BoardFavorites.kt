package com.chikuwadon.hissidroidplus

import android.content.Context

/** Shared with the existing HomeActivity private preferences; no migration or duplicate list. */
object BoardFavorites {
    private const val KEY = "favorite_boards"
    private fun prefs(context: Context) =
        context.getSharedPreferences(HomeActivity::class.java.simpleName, Context.MODE_PRIVATE)

    @JvmStatic fun valid(code: String): Boolean = code.matches(Regex("[A-Za-z0-9_]{1,60}"))

    @JvmStatic fun normalized(codes: Collection<String>): List<String> =
        codes.filter(::valid).distinct().sorted()

    fun read(context: Context): List<String> = normalized(prefs(context).getStringSet(KEY, emptySet()) ?: emptySet())

    fun add(context: Context, code: String) {
        require(valid(code))
        val existing = HashSet(prefs(context).getStringSet(KEY, emptySet()) ?: emptySet())
        existing.add(code)
        prefs(context).edit().putStringSet(KEY, existing).apply()
    }

    fun clear(context: Context) { prefs(context).edit().remove(KEY).apply() }
}
