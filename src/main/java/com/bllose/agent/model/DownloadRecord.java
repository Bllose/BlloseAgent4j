package com.bllose.agent.model;

import java.time.LocalDateTime;

public class DownloadRecord {
    private Long id;
    private String externalId;
    private String idType;
    private String sourceUrl;
    private String fileName;
    private Long fileSize;
    private LocalDateTime createdAt;

    public DownloadRecord() {}

    public DownloadRecord(Long id, String externalId, String idType, String sourceUrl,
                          String fileName, Long fileSize, LocalDateTime createdAt) {
        this.id = id;
        this.externalId = externalId;
        this.idType = idType;
        this.sourceUrl = sourceUrl;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
