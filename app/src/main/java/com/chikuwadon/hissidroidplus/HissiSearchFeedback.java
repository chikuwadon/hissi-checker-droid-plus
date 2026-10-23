package com.chikuwadon.hissidroidplus;

/** Never label an arbitrary HTTP error as a successful empty search. */
public final class HissiSearchFeedback {
    private HissiSearchFeedback(){}
    public static boolean missingSearchResult(CheckerQuery query,String error){
        return query!=null && query.site==CheckerQuery.Site.HISSI &&
                query.url.isEmpty() && error!=null && error.trim().equals("HTTP 404");
    }
}
