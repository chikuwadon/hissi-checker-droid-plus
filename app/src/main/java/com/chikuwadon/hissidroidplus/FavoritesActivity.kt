package com.chikuwadon.hissidroidplus

/** Separate destination allows history and favorites to retain their own screen state. */
class FavoritesActivity : HistoryActivity() {
    override val favoritesScreen: Boolean = true
}
