package com.bllose.agent.model;

import java.time.LocalDateTime;

public class ChatMessage {

    private Long id;
    private String chatId;
    private Integer turnNum;
    private String type;
    private String thinking;
    private String message;
    private LocalDateTime createdAt;

    public ChatMessage() {}

    public ChatMessage(Long id, String chatId, Integer turnNum, String type,
                       String thinking, String message, LocalDateTime createdAt) {
        this.id = id;
        this.chatId = chatId;
        this.turnNum = turnNum;
        this.type = type;
        this.thinking = thinking;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public Integer getTurnNum() { return turnNum; }
    public void setTurnNum(Integer turnNum) { this.turnNum = turnNum; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getThinking() { return thinking; }
    public void setThinking(String thinking) { this.thinking = thinking; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
