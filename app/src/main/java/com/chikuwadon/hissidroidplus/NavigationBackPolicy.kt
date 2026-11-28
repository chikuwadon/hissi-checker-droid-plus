package com.chikuwadon.hissidroidplus

/** Connected launches may return to a live checker host after explicit peer navigation.
 *  A stale saved navigation flag must not recreate a host and trap the external caller. */
object NavigationBackPolicy {
    @JvmStatic
    fun exitConnectedResult(connected: Boolean, enteredViaNavigation: Boolean, hasHome: Boolean): Boolean =
        connected && (!enteredViaNavigation || !hasHome)
}

/** Chronological, task-local tab visits. A Back consumes a visit instead of adding one. */
class TabHistory {
    private val visits = ArrayList<MainNavigation.Destination>()
    fun visit(destination: MainNavigation.Destination) {
        if (visits.lastOrNull() == destination) return
        visits.add(destination)
        if (visits.size > 100) visits.removeAt(0)
    }
    fun previous(current: MainNavigation.Destination,
                 available: (MainNavigation.Destination) -> Boolean): MainNavigation.Destination? {
        if (visits.lastOrNull() == current) visits.removeAt(visits.lastIndex)
        while (visits.isNotEmpty()) {
            val candidate = visits.last()
            if (candidate != current && available(candidate)) return candidate
            visits.removeAt(visits.lastIndex)
        }
        visit(current)
        return null
    }
}
