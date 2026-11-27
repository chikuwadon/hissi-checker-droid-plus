package com.chikuwadon.hissidroidplus

/** Lightweight board identity parsed from the official 5ch board menu. */
class BoardCatalog private constructor() {
    class Board @JvmOverloads constructor(
        @JvmField val code: String,
        @JvmField val name: String,
        @JvmField val address: String = ""
    ) {
        override fun toString(): String = name + " (" + code + ")"
    }
}
