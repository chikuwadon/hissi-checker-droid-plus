package com.chikuwadon.hissidroidplus

import android.content.Context

/** Store only provider and board selection, not query text or results. */
object ArchiveSelectionStore {
    private fun prefs(context: Context) = context.getSharedPreferences("archive_selection", Context.MODE_PRIVATE)
    @JvmStatic fun provider(context: Context): ArchiveProvider =
        parseProvider(prefs(context).getString("provider", null))

    @JvmStatic fun parseProvider(raw: String?): ArchiveProvider =
        ArchiveProvider.entries.firstOrNull { it.name == raw } ?: ArchiveProvider.EDGE_ARCHIVE

    @JvmStatic fun draft(context: Context, provider: ArchiveProvider): ArchiveQuery =
        restoreDraft(prefs(context).getString(provider.name + "_code", "").orEmpty(),
            prefs(context).getString(provider.name + "_name", "").orEmpty())

    @JvmStatic fun restoreDraft(code: String, name: String): ArchiveQuery =
        if (BoardFavorites.valid(code)) ArchiveQuery("", boardCode = code, boardName = name.take(100).ifBlank { code })
        else ArchiveQuery("")

    fun save(context: Context, provider: ArchiveProvider, draft: ArchiveQuery) {
        val safe = restoreDraft(draft.boardCode, draft.boardName)
        prefs(context).edit().putString("provider", provider.name)
            .putString(provider.name + "_code", safe.boardCode)
            .putString(provider.name + "_name", safe.boardName).apply()
    }
}
