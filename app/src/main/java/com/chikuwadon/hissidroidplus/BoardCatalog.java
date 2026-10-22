package com.chikuwadon.hissidroidplus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Board data obtained from the user-provided original v1.5 DEX string pool. */
public final class BoardCatalog {
    public static final class Board {
        public final String code, name;
        public Board(String code, String name) {this.code=code;this.name=name;}
        @Override public String toString(){return name+" ("+code+")";}
    }
    public static List<Board> read(Reader source) throws IOException {
        List<Board> all=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(source)){
            String line;
            while((line=reader.readLine())!=null){
                int tab=line.indexOf('\t');
                if(tab<1||tab==line.length()-1)continue;
                all.add(new Board(line.substring(0,tab),line.substring(tab+1)));
            }
        }
        return Collections.unmodifiableList(all);
    }
}
