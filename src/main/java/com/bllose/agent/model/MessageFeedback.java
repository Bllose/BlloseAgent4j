package com.bllose.agent.model;

import java.time.LocalDateTime;

public class MessageFeedback {

    private Long id;
    private String chatId;
    private Integer turnNum;
    private String userIdentifier;
    private String userType;
    private String rating;
    private String feedbackText;
    private String messageHistory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MessageFeedback() {}

    public MessageFeedback(Long id, String chatId, Integer turnNum, String userIdentifier,
                           String userType, String rating, String feedbackText,
                           String messageHistory, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.chatId = chatId;
        this.turnNum = turnNum;
        this.userIdentifier = userIdentifier;
        this.userType = userType;
        this.rating = rating;
        this.feedbackText = feedbackText;
        this.messageHistory = messageHistory;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public Integer getTurnNum() { return turnNum; }
    public void setTurnNum(Integer turnNum) { this.turnNum = turnNum; }
    public String getUserIdentifier() { return userIdentifier; }
    public void setUserIdentifier(String userIdentifier) { this.userIdentifier = userIdentifier; }
    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getFeedbackText() { return feedbackText; }
    public void setFeedbackText(String feedbackText) { this.feedbackText = feedbackText; }
    public String getMessageHistory() { return messageHistory; }
    public void setMessageHistory(String messageHistory) { this.messageHistory = messageHistory; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
