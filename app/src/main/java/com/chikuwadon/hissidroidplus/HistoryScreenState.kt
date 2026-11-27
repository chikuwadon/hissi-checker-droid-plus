package com.chikuwadon.hissidroidplus

import androidx.lifecycle.ViewModel

/** UI state only: no Activity, View, database or network references. */
class HistoryScreenState : ViewModel() {
    var initialized = false
    var filter = HistoryFilter()
    var sort = 0
    var selection = false
    val chosen = HashSet<String>()
}
