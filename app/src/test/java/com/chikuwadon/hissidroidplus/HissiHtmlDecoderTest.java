package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class HissiHtmlDecoderTest {
    @Test public void mislabeledShiftJisCanBeRecovered(){
        String original="<html>書き込みレス一覧 本文 テスト</html>";
        byte[] shiftJis=original.getBytes(Charset.forName("Shift_JIS"));
        String decoded=HissiHtmlDecoder.decode(shiftJis,StandardCharsets.UTF_8,true);
        assertTrue(decoded.endsWith(original));
        assertTrue(decoded.startsWith(HissiHtmlDecoder.FALLBACK_MARKER));
    }
    @Test public void validUtf8AndExistingReplacementStayUnchanged(){
        String original="<html>書き込み�レス 本文</html>";
        byte[] utf8=original.getBytes(StandardCharsets.UTF_8);
        assertEquals(original,HissiHtmlDecoder.decode(utf8,StandardCharsets.UTF_8,true));
        assertEquals(original,HissiHtmlDecoder.decode(utf8,StandardCharsets.UTF_8,false));
    }
}
