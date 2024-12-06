package com.ll.simpleDb;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Article {
    private  long id;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private String title;
    private String body;
    private String author;
    private boolean isBlind;

//    public  Article(Map<String,Object> row){
//        this.id = (long) row.get("id");
//        this.createdDate = (LocalDateTime) row.get("createdDate");
//        this.modifiedDate = (LocalDateTime) row.get("modifiedDate");
//        this.title = (String) row.get("title");
//        this.title = (String) row.get("body");
//        this.isBlind = (boolean) row.get("isBlind");
//    }
}
