package com.chikuwadon.hissidroidplus;

/** Lightweight board identity parsed from the official 5ch menu. */
public final class BoardCatalog {
    private BoardCatalog(){}
    public static final class Board {
        public final String code, name;
        public Board(String code,String name){this.code=code;this.name=name;}
        @Override public String toString(){return name+" ("+code+")";}
    }
}
