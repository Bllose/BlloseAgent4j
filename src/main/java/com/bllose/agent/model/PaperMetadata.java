package com.bllose.agent.model;

import java.time.LocalDateTime;

public class PaperMetadata {
    private Long id;
    private String doi;
    private String title;
    private String authors;
    private String publicationDate;
    private Integer year;
    private String journal;
    private String volume;
    private String issue;
    private String pages;
    private String publisher;
    private Integer citationCount;
    private Integer influentialCitationCount;
    private String url;
    private String pdfUrl;
    private String bibtex;
    private String issn;
    private Boolean isOpenAccess;
    private String publicationTypes;
    private String paperAbstract;
    private Integer referenceCount;
    private String retractionStatus;
    private String sourceQuality;
    private String sourcesJson;
    private Long downloadRecordId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PaperMetadata() {}

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDoi() { return doi; }
    public void setDoi(String doi) { this.doi = doi; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthors() { return authors; }
    public void setAuthors(String authors) { this.authors = authors; }

    public String getPublicationDate() { return publicationDate; }
    public void setPublicationDate(String publicationDate) { this.publicationDate = publicationDate; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getJournal() { return journal; }
    public void setJournal(String journal) { this.journal = journal; }

    public String getVolume() { return volume; }
    public void setVolume(String volume) { this.volume = volume; }

    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }

    public String getPages() { return pages; }
    public void setPages(String pages) { this.pages = pages; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public Integer getCitationCount() { return citationCount; }
    public void setCitationCount(Integer citationCount) { this.citationCount = citationCount; }

    public Integer getInfluentialCitationCount() { return influentialCitationCount; }
    public void setInfluentialCitationCount(Integer influentialCitationCount) { this.influentialCitationCount = influentialCitationCount; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public String getBibtex() { return bibtex; }
    public void setBibtex(String bibtex) { this.bibtex = bibtex; }

    public String getIssn() { return issn; }
    public void setIssn(String issn) { this.issn = issn; }

    public Boolean getIsOpenAccess() { return isOpenAccess; }
    public void setIsOpenAccess(Boolean isOpenAccess) { this.isOpenAccess = isOpenAccess; }

    public String getPublicationTypes() { return publicationTypes; }
    public void setPublicationTypes(String publicationTypes) { this.publicationTypes = publicationTypes; }

    public String getPaperAbstract() { return paperAbstract; }
    public void setPaperAbstract(String paperAbstract) { this.paperAbstract = paperAbstract; }

    public Integer getReferenceCount() { return referenceCount; }
    public void setReferenceCount(Integer referenceCount) { this.referenceCount = referenceCount; }

    public String getRetractionStatus() { return retractionStatus; }
    public void setRetractionStatus(String retractionStatus) { this.retractionStatus = retractionStatus; }

    public String getSourceQuality() { return sourceQuality; }
    public void setSourceQuality(String sourceQuality) { this.sourceQuality = sourceQuality; }

    public String getSourcesJson() { return sourcesJson; }
    public void setSourcesJson(String sourcesJson) { this.sourcesJson = sourcesJson; }

    public Long getDownloadRecordId() { return downloadRecordId; }
    public void setDownloadRecordId(Long downloadRecordId) { this.downloadRecordId = downloadRecordId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
