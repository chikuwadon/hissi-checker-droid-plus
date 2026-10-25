package com.chikuwadon.hissidroidplus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Resolve an external Edge board/date/ID into an actual checker page.
 * A ChMate Intent carries no kyodemo thread key, so it cannot be sent to
 * hissi.org (5ch) or to a fabricated Edge checker URL.
 */
public final class EdgeInboundSearch {
    private EdgeInboundSearch(){}

    public static CheckerQuery resolve(String board,String date,String id)throws IOException{
        String searchUrl=EdgeSearchLink.searchUrl(board,date,id);
        String html=HttpTransport.get(searchUrl,StandardCharsets.UTF_8);
        List<EdgeSearchResults.Item> matches=EdgeSearchResults.parse(
                html,searchUrl,board,date,id);
        CheckerQuery unverified=null;
        int inspected=0;
        for(EdgeSearchResults.Item item:matches){
            if(inspected++>=6)break;
            CheckerQuery candidate;
            try{candidate=CheckerQuery.parseUrl(item.checkerUrl);}
            catch(IllegalArgumentException invalid){continue;}
            try{
                CheckerResult page=new EdgeAdapter().loadForVerification(candidate);
                if(page.posts.isEmpty()){
                    if(unverified==null)unverified=candidate;
                }else if(!EdgeSearchValidation.matchingPosts(page,date,id).posts.isEmpty()){
                    return candidate;
                }
            }catch(IOException inaccessible){
                // Native HTTP can be blocked while the Activity's WebView
                // fallback succeeds. Preserve date/ID verification there.
                if(unverified==null)unverified=candidate;
            }
        }
        return unverified;
    }
}
