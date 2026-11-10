package com.chikuwadon.hissidroidplus

/** Lightweight board identity parsed from the official 5ch board menu. */
class BoardCatalog private constructor() {
    class Board(
        @JvmField val code: String,
        @JvmField val name: String
    ) {
        override fun toString(): String = name + " (" + code + ")"
    }
}
