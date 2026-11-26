package com.chikuwadon.hissidroidplus

import androidx.lifecycle.ViewModel

/** Retains only the parsed model and pagination state, never Views, Contexts or workers. */
class ResultScreenState : ViewModel() {
    data class Snapshot(
        val query: CheckerQuery,
        val result: CheckerResult,
        val nextPage: Int,
        val noMorePages: Boolean,
        val edgeDiagnostic: String
    )

    var snapshot: Snapshot? = null
}
