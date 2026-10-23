package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public final class NetworkSecurityConfigTest {
    @Test public void noGlobalCleartextPermission() throws Exception {
        String xml=new String(Files.readAllBytes(Paths.get("src/main/res/xml/network_security_config.xml")),StandardCharsets.UTF_8);
        String manifest=new String(Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),StandardCharsets.UTF_8);
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""));
        assertFalse(manifest.contains("android:usesCleartextTraffic=\"true\""));
        assertTrue(xml.contains("<base-config cleartextTrafficPermitted=\"false\""));
        assertTrue(xml.contains("<domain includeSubdomains=\"false\">hissi.org</domain>"));
        assertTrue(xml.contains("<domain includeSubdomains=\"false\">www.hissi.org</domain>"));
        assertFalse(xml.contains("includeSubdomains=\"true\""));
        assertFalse(xml.contains("<domain>kyodemo.net</domain>"));
    }
}
