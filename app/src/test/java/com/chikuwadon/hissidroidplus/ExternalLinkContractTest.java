package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ExternalLinkContractTest {
    private static final String ANDROID_NS="http://schemas.android.com/apk/res/android";

    @Test public void resultsAreExternallyLaunchableWithoutRouter() throws Exception {
        Path path=java.nio.file.Paths.get("src/main/AndroidManifest.xml");
        String xml=new String(Files.readAllBytes(path),StandardCharsets.UTF_8);
        assertFalse("Avoid intermediate trampoline activities",xml.contains("LinkRouterActivity"));
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        org.w3c.dom.Document doc=factory.newDocumentBuilder().parse(path.toFile());
        NodeList activities=doc.getElementsByTagName("activity");
        Element result=null;
        for(int i=0;i<activities.getLength();i++){
            Element element=(Element)activities.item(i);
            if(".ResultActivity".equals(element.getAttributeNS(ANDROID_NS,"name")))result=element;
        }
        assertNotNull(result);
        assertEquals("true",result.getAttributeNS(ANDROID_NS,"exported"));
        String signatures=result.getTextContent();
        // Nested <action> tags have no text; verify their attributes individually.
        NodeList actions=result.getElementsByTagName("action");
        boolean view=false,send=false;
        for(int i=0;i<actions.getLength();i++){
            String name=((Element)actions.item(i)).getAttributeNS(ANDROID_NS,"name");
            view|="android.intent.action.VIEW".equals(name);
            send|="android.intent.action.SEND".equals(name);
        }
        assertTrue("Must support deep links from ChMate",view);
        assertTrue("Must support text/plain sharing",send);
        assertTrue(xml.contains("hissi.org"));
        assertTrue(xml.contains("kyodemo.net"));
        assertTrue("ChMate uses text/* MIME matching",xml.contains("android:mimeType=\"text/*\""));
    }

    @Test public void parsesChMateUrlVariants() {
        for(String url:new String[]{
            "http://hissi.org/read.php/android/20261022/example.html",
            "https://hissi.org/read.php/android/20261022/example.html",
            "https://www.hissi.org/read.php/android/20261022/example.html"}){
            assertEquals(CheckerQuery.Site.HISSI,CheckerQuery.parseUrl(url).site);
        }
    }
}
