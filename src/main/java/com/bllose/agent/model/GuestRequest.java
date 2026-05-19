package com.bllose.agent.model;

public record GuestRequest(
    String fingerprint,
    String userAgent,
    String platform,
    String screenInfo,
    String language,
    String timezone,
    String hostname
) {}
