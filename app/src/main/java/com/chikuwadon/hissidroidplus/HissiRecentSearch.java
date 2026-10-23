package com.chikuwadon.hissidroidplus;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Bounded seven-day ID lookup. 404 means that day's result URL is absent. */
public final class HissiRecentSearch {
    public interface Probe {
        boolean hasPosts(String board,String date,String id) throws IOException;
    }
    public static final class Result {
        public final String day,error;
        public final int checkedDays;
        Result(String day,String error,int checkedDays){
            this.day=day;this.error=error;this.checkedDays=checkedDays;
        }
    }
    private HissiRecentSearch(){}
    public static Result find(String board,String id,LocalDate today,Probe probe){
        if(today==null||probe==null)throw new IllegalArgumentException("Date or fetcher missing");
        CheckerQuery.hissi(board,today.format(DateTimeFormatter.BASIC_ISO_DATE),id);
        for(int i=0;i<7;i++){
            String date=today.minusDays(i).format(DateTimeFormatter.BASIC_ISO_DATE);
            try{
                if(probe.hasPosts(board,date,id))return new Result(date,null,i+1);
            }catch(IOException e){
                if("HTTP 404".equals(e.getMessage()))continue;
                return new Result(null,"通信・解析エラー ("+
                    (e.getMessage()==null?"原因不明":e.getMessage())+")",i+1);
            }
        }
        return new Result(null,null,7);
    }
}
