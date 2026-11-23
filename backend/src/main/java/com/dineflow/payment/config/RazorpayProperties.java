package com.dineflow.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dineflow.payment.razorpay")
public class RazorpayProperties {

    private boolean enabled = true;
    private String keyId = "rzp_test_placeholder_key_id";
    private String keySecret = "placeholder_key_secret";
    private String webhookSecret = "placeholder_webhook_secret";
    private String baseUrl = "https://api.razorpay.com/v1";
    private long connectTimeoutMs = 5000;
    private long readTimeoutMs = 10000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getKeySecret() { return keySecret; }
    public void setKeySecret(String keySecret) { this.keySecret = keySecret; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public long getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(long connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public long getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(long readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
