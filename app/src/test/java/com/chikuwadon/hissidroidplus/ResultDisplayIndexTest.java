package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class ResultDisplayIndexTest {
    private CheckerResult.Post post(String title, String name, String url) {
        return new CheckerResult.Post(title, name, "2026/11/26 12:34:56", "本文", url);
    }

    @Test public void displayIndexMatchesExistingIndependentAggregation() {
        CheckerResult result = new CheckerResult();
        result.names.add("既存の名前");
        result.names.add("名無し [sage]");
        result.posts.add(post("第一", "名無し [sage]", "http://egg.5ch.io/test/read.cgi/android/1792500780/1"));
        result.posts.add(post("第一", "名無し", "https://egg.5ch.net/test/read.cgi/android/1792500780/2"));
        result.posts.add(post("", "別名", "https://bbs.eddibb.cc/test/read.cgi/liveedge/1792500781/3"));
        result.posts.add(post("", "別名", ""));
        result.threads.put("https://bbs.eddibb.cc/test/read.cgi/liveedge/1792500782/", "一覧のみ");
        PostIndex.DisplayIndex index = PostIndex.displayIndex(result);
        assertEquals(2, result.names.size()); // building the snapshot does not mutate the source
        PostIndex.collectNames(result);
        assertEquals(result.names, index.getNames());
        assertEquals(PostIndex.namePostCounts(result), index.getNameCounts());
        Map<String,String> expected = PostIndex.uniqueThreads(result);
        assertEquals(expected.size(), index.getThreads().size());
        Map<String,Integer> counts = PostIndex.threadPostCounts(result);
        int i = 0;
        for (Map.Entry<String,String> e : expected.entrySet()) {
            PostIndex.ThreadRow row = index.getThreads().get(i++);
            assertEquals(e.getKey(), row.getKey());
            assertEquals(e.getValue(), row.getValue());
            assertEquals(counts.getOrDefault(PostIndex.threadKey(e.getKey()),0).intValue(), row.getCount());
        }
        assertEquals(result.posts.size(), index.getPostKeys().size());
        assertEquals(index.getPostKeys().get(0), index.getPostKeys().get(1));
        for (i=0;i<result.posts.size();i++)
            assertEquals(PostIndex.threadKey(result.posts.get(i).link), index.getPostKeys().get(i));
    }

    @Test public void newPageRebuildsKeysAndCountsWithoutChangingPreviousSnapshot() {
        CheckerResult result = new CheckerResult();
        String url = "https://bbs.eddibb.cc/test/read.cgi/liveedge/1792500781/";
        result.posts.add(post("スレ", "名前", url+"1"));
        PostIndex.DisplayIndex before = PostIndex.displayIndex(result);
        result.posts.add(post("スレ", "名前", url+"2"));
        PostIndex.DisplayIndex after = PostIndex.displayIndex(result);
        assertEquals(1, before.getPostKeys().size());
        assertEquals(1, before.getThreads().get(0).getCount());
        assertEquals(2, after.getPostKeys().size());
        assertEquals(2, after.getThreads().get(0).getCount());
        assertEquals(Integer.valueOf(2), after.getNameCounts().get("名前"));
        assertEquals(after.getPostKeys().get(0), after.getPostKeys().get(1));
    }

    @Test public void emptyResultAndHeaderCacheContracts() throws Exception {
        PostIndex.DisplayIndex empty = PostIndex.displayIndex(new CheckerResult());
        assertTrue(empty.getNames().isEmpty());
        assertTrue(empty.getPostKeys().isEmpty());
        assertTrue(empty.getThreads().isEmpty());
        assertTrue(empty.getNameCounts().isEmpty());
        String activity = Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/ResultActivity.kt"), StandardCharsets.UTF_8);
        assertTrue(activity.contains("PostIndex.displayIndex(result)"));
        assertTrue(activity.contains("postKeys[index] == postKeys[index - 1]"));
        assertTrue(activity.contains("val count = entry.count"));
        assertTrue(activity.contains("LruCache<CheckerResult.Post, String>(128)"));
        assertTrue(activity.contains("if (it.length <= 4096) headerCache.put(post, it)"));
        assertTrue(activity.contains("headerCache.evictAll()"));
    }
}
