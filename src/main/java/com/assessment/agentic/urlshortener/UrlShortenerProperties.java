package com.assessment.agentic.urlshortener;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentic.url-shortener")
public class UrlShortenerProperties {

    private Duration defaultTtl = Duration.ofDays(30);
    private int codeLength = 8;
    private String regionPrefix = "us";
    private Set<String> blockedHosts = new LinkedHashSet<>();
    private int rateLimitPerMinute = 60;
    private Duration redirectEventRetention = Duration.ofDays(90);
    private Duration inactiveUrlRetention = Duration.ofDays(30);

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }

    public String getRegionPrefix() {
        return regionPrefix;
    }

    public void setRegionPrefix(String regionPrefix) {
        this.regionPrefix = regionPrefix;
    }

    public Set<String> getBlockedHosts() {
        return blockedHosts;
    }

    public void setBlockedHosts(Set<String> blockedHosts) {
        this.blockedHosts = blockedHosts;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public Duration getRedirectEventRetention() {
        return redirectEventRetention;
    }

    public void setRedirectEventRetention(Duration redirectEventRetention) {
        this.redirectEventRetention = redirectEventRetention;
    }

    public Duration getInactiveUrlRetention() {
        return inactiveUrlRetention;
    }

    public void setInactiveUrlRetention(Duration inactiveUrlRetention) {
        this.inactiveUrlRetention = inactiveUrlRetention;
    }
}
