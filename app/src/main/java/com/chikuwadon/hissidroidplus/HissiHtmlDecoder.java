package com.chikuwadon.hissidroidplus;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Preserve valid UTF-8; recover Hissi responses incorrectly labeled UTF-8 only when raw bytes are malformed. */
public final class HissiHtmlDecoder {
    public static final String FALLBACK_MARKER="\u0001HISSI_SHIFT_JIS\u0001";
    private static final Charset SJIS=Charset.forName("Shift_JIS");
    private HissiHtmlDecoder(){}
    static String decode(byte[] bytes,Charset declared,boolean hissi){
        String initial=new String(bytes,declared);
        if(!hissi||!declared.equals(StandardCharsets.UTF_8))return initial;
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
            // U+FFFD already present in valid source bytes cannot be recovered.
            return initial;
        } catch(CharacterCodingException invalidUtf8){
            String alternative=new String(bytes,SJIS);
            if(countReplacement(alternative)<countReplacement(initial))
                return FALLBACK_MARKER+alternative;
            return initial;
        }
    }
    static int countReplacement(String s){
        int n=0;
        for(int i=0;i<s.length();i++)if(s.charAt(i)=='\uFFFD')n++;
        return n;
    }
}
