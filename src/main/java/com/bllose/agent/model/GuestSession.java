package com.bllose.agent.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GuestSession {
    private Long id;
    private String fingerprint;
    private String fingerprintHash;
    private String guestName;
    private LocalDateTime firstLogin;
    private LocalDateTime lastLogin;
    private LocalDateTime authExpiry;
    private String lastSession;
    private int requestCount;
    private List<LocalDateTime> lastVisits = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GuestSession() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getFingerprintHash() { return fingerprintHash; }
    public void setFingerprintHash(String fingerprintHash) { this.fingerprintHash = fingerprintHash; }
    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public LocalDateTime getFirstLogin() { return firstLogin; }
    public void setFirstLogin(LocalDateTime firstLogin) { this.firstLogin = firstLogin; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
    public LocalDateTime getAuthExpiry() { return authExpiry; }
    public void setAuthExpiry(LocalDateTime authExpiry) { this.authExpiry = authExpiry; }
    public String getLastSession() { return lastSession; }
    public void setLastSession(String lastSession) { this.lastSession = lastSession; }
    public int getRequestCount() { return requestCount; }
    public void setRequestCount(int requestCount) { this.requestCount = requestCount; }
    public List<LocalDateTime> getLastVisits() { return lastVisits; }
    public void setLastVisits(List<LocalDateTime> lastVisits) { this.lastVisits = lastVisits; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
