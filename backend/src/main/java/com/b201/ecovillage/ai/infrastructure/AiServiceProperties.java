package com.b201.ecovillage.ai.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ecovillage.ai-service")
public class AiServiceProperties {

    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:8000";
    private String token = "local-development-token";
    private Duration connectTimeout = Duration.ofSeconds(2);
    // 투자 이야기 한 건이 5초 안팎 걸린다. 읽기 제한이 이보다 빠듯하면 서비스가 아직
    // 만들고 있는데 호출한 쪽이 먼저 포기해 버린다.
    private Duration readTimeout = Duration.ofSeconds(20);
    // 재시도까지 감안한 전체 예산이다. 이 값이 곧 AI 서비스에 알려 주는 deadline이므로
    // 읽기 제한보다 넉넉해야 한다.
    private Duration totalTimeout = Duration.ofSeconds(75);
    private int maxAttempts = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getTotalTimeout() {
        return totalTimeout;
    }

    public void setTotalTimeout(Duration totalTimeout) {
        this.totalTimeout = totalTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = Math.max(1, Math.min(4, maxAttempts));
    }
}
