package com.chikuwadon.hissidroidplus;

import android.content.Context;
import android.util.AttributeSet;
import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Regression for the Android 16 crash in ChMate-launched ResultActivity. */
public final class HourlyChartInflationContractTest {
    @Test public void chartSupportsXmlInflationSignature() throws Exception {
        assertNotNull(HourlyChartView.class.getConstructor(Context.class,AttributeSet.class));
        assertNotNull(HourlyChartView.class.getConstructor(Context.class,AttributeSet.class,int.class));
    }
    @Test public void resultsLayoutInflatesChart() throws Exception {
        String xml=new String(Files.readAllBytes(Paths.get(
                "src/main/res/layout/activity_results.xml")),StandardCharsets.UTF_8);
        assertTrue(xml.contains("com.chikuwadon.hissidroidplus.HourlyChartView"));
    }
}
