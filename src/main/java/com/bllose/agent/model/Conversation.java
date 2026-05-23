package com.bllose.agent.model;

import java.time.LocalDateTime;

public class Conversation {

    private String chatId;
    private Integer userNumber;
    private String title;
    private LocalDateTime createdAt;

    public Conversation() {}

    public Conversation(String chatId, Integer userNumber, String title, LocalDateTime createdAt) {
        this.chatId = chatId;
        this.userNumber = userNumber;
        this.title = title;
        this.createdAt = createdAt;
    }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public Integer getUserNumber() { return userNumber; }
    public void setUserNumber(Integer userNumber) { this.userNumber = userNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
