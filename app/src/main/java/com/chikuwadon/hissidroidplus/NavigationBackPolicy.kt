package com.chikuwadon.hissidroidplus

/** Connected launches may return to a live checker host after explicit peer navigation.
 *  A stale saved navigation flag must not recreate a host and trap the external caller. */
object NavigationBackPolicy {
    @JvmStatic
    fun exitConnectedResult(connected: Boolean, enteredViaNavigation: Boolean, hasHome: Boolean): Boolean =
        connected && (!enteredViaNavigation || !hasHome)
}
