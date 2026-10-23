package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class EdgeSearchValidationTest {
    private static final String LINK="https://www.kyodemo.net/sdemo/r/e_e_liveedge/1792726415/";
    private static CheckerResult.Post post(String meta,String body){
        return new CheckerResult.Post("テストスレ","エッヂの名無し",meta,body,LINK);
    }
    @Test public void requestedDateAndIdMustBePresentInActualPostHeader(){
        CheckerResult source=new CheckerResult();
        source.reportedPostCount=4;
        source.reportedThreadCount=2;
        source.threads.put(LINK,"テストスレ");
        source.posts.add(post("1 エッヂの名無し 2026/10/22(木) 22:09:00 ID:AbC123","別の日"));
        source.posts.add(post("2 エッヂの名無し 2026/10/23(金) 00:01:00 ID:Wrong","IDが違う"));
        source.posts.add(post("3 エッヂの名無し 2026/10/23(金) 10:05:33.451 ID:AbC123","一致"));
        source.posts.add(post("4 エッヂの名無し ID:AbC123","日付が不明"));
        CheckerResult result=EdgeSearchValidation.matchingPosts(source,"20261023","AbC123");
        assertEquals(1,result.posts.size());
        assertEquals("一致",result.posts.get(0).body);
        assertEquals(1,result.reportedPostCount);
        assertEquals(1,result.reportedThreadCount);
        assertEquals(1,result.threads.size());
        assertEquals(1,result.names.size());
    }
    @Test public void wrongDayWithSameIdNeverCountsAsAHit(){
        CheckerResult data=new CheckerResult();
        data.posts.add(post("1 名無し 2026/10/22(木) 13:22:00 ID:SameID","今日ではない"));
        assertEquals(0,EdgeSearchValidation.matchingPosts(data,"20261024","SameID").posts.size());
    }
    @Test public void actualIdAndDateAreCaseSensitiveAndNotDerivedFromBody(){
        CheckerResult data=new CheckerResult();
        data.posts.add(post("2026/10/23 12:00:00 ID:AbC123",""));
        assertEquals(0,EdgeSearchValidation.matchingPosts(data,"20261023","abc123").posts.size());
        data.posts.add(post("2026/10/23 12:00:00","本文ID:AbC123"));
        assertEquals(1,EdgeSearchValidation.matchingPosts(data,"20261023","AbC123").posts.size());
    }
    @Test public void kyodemoStructuredPostDatesAreVerified(){
        CheckerQuery q=CheckerQuery.parseUrl(
            "https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=AbC123&key=1792726415&date=20261023");
        String html="<main><div class='box content'><h2><a href='"+LINK+"'>テスト</a></h2>"+
            "<div class='post'><div class='r-head'><a href='"+LINK+"9'>9</a>"+
            ":エッヂの名無し 2026/10/22(木) 22:01:00 ID:AbC123</div>古い投稿</div>"+
            "<div class='post'><div class='r-head'><a href='"+LINK+"10'>10</a>"+
            ":エッヂの名無し 2026/10/23(金) 08:00:00 ID:AbC123</div>当日投稿</div></div></main>";
        CheckerResult parsed=EdgeAdapter.parse(html,q);
        assertEquals(2,parsed.posts.size());
        CheckerResult filtered=EdgeSearchValidation.matchingPosts(parsed,q.date,q.id);
        assertEquals(1,filtered.posts.size());
        assertEquals("当日投稿",filtered.posts.get(0).body);
    }
}
