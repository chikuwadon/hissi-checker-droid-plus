package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class HttpResponseReaderTest {
    @Test public void exactLimitAndEmptyResponseAreAccepted() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        assertArrayEquals(bytes, HttpResponseReader.read(new ByteArrayInputStream(bytes), 4, 4));
        assertArrayEquals(bytes, HttpResponseReader.read(new ByteArrayInputStream(bytes), 4, -1));
        assertEquals(0, HttpResponseReader.read(new ByteArrayInputStream(new byte[0]), 4, 0).length);
    }

    @Test public void declaredOversizeIsRejectedBeforeRead() throws Exception {
        InputStream stream = new InputStream() {
            @Override public int read() { throw new AssertionError("unexpected read"); }
        };
        try {
            HttpResponseReader.read(stream, 4, 5);
            fail("oversized response accepted");
        } catch (IOException expected) {
            assertEquals("HTMLが大きすぎます", expected.getMessage());
        }
    }

    @Test public void streamedOversizeIsRejectedEvenWithoutContentLength() throws Exception {
        try {
            HttpResponseReader.read(new ByteArrayInputStream(new byte[5]), 4, -1);
            fail("oversized response accepted");
        } catch (IOException expected) {
            assertEquals("HTMLが大きすぎます", expected.getMessage());
        }
    }

    @Test public void zeroLengthReadMakesProgress() throws Exception {
        InputStream stream = new InputStream() {
            int position = 0;
            @Override public int read(byte[] b, int off, int len) { return 0; }
            @Override public int read() { return position < 3 ? ++position : -1; }
        };
        assertArrayEquals(new byte[]{1, 2, 3}, HttpResponseReader.read(stream, 3, -1));
    }

    @Test public void interruptedThreadStopsWithoutClearingFlag() throws Exception {
        Thread.currentThread().interrupt();
        try {
            HttpResponseReader.read(new ByteArrayInputStream(new byte[]{1}), 4, 1);
            fail("interruption ignored");
        } catch (InterruptedIOException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void interruptionDuringReadStopsBeforeAppending() throws Exception {
        InputStream stream = new InputStream() {
            @Override public int read(byte[] b, int off, int len) {
                Thread.currentThread().interrupt();
                b[off] = 7;
                return 1;
            }
            @Override public int read() { return 7; }
        };
        try {
            HttpResponseReader.read(stream, 4, -1);
            fail("interruption ignored");
        } catch (InterruptedIOException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void diagnosticsRemainSeparateAcrossWorkers() throws Exception {
        HttpRequestDiagnostics diagnostics = new HttpRequestDiagnostics();
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        AtomicReference<String> a = new AtomicReference<>();
        AtomicReference<String> b = new AtomicReference<>();
        Thread one = new Thread(() -> {
            diagnostics.set("HTTP=200");
            first.countDown();
            try {
                if (second.await(3, TimeUnit.SECONDS)) a.set(diagnostics.get());
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread two = new Thread(() -> {
            try {
                if (first.await(3, TimeUnit.SECONDS)) {
                    diagnostics.set("HTTP=503");
                    b.set(diagnostics.get());
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { second.countDown(); }
        });
        one.start();
        two.start();
        one.join(4000);
        two.join(4000);
        assertFalse(one.isAlive());
        assertFalse(two.isAlive());
        assertEquals("HTTP=200", a.get());
        assertEquals("HTTP=503", b.get());
        assertEquals("未取得", diagnostics.get());
    }
}
