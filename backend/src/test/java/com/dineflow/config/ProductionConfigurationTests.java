package com.dineflow.config;

import com.dineflow.payment.config.RazorpayProperties;
import com.dineflow.payment.provider.MockPaymentGatewayProvider;
import com.dineflow.security.CookieHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProductionConfigurationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private CookieHelper cookieHelper;

    private RazorpayProperties validRazorpay() {
        RazorpayProperties props = new RazorpayProperties();
        props.setEnabled(true);
        props.setKeyId("rzp_live_1234567890");
        props.setKeySecret("real_live_secret_9876543210");
        props.setWebhookSecret("real_webhook_secret_abcdef");
        return props;
    }

    @Test
    @DisplayName("Valid production configuration passes startup validator cleanly")
    void validProdConfigPasses() {
        assertThatCode(() -> ProductionConfigurationValidator.validate(
                "prod-ultra-strong-jwt-signing-secret-key-2026-xyz",
                "prod-qr-signing-salt-2026-abc",
                validRazorpay(),
                List.of(),
                "https://app.dineflow.example.com",
                true
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Missing or dev-default JWT secret fails fast in production")
    void devDefaultJwtSecretFailsFast() {
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                ProductionConfigurationValidator.DEFAULT_DEV_JWT_SECRET,
                "prod-qr-signing-salt-2026-abc",
                validRazorpay(),
                List.of(),
                "https://app.dineflow.example.com",
                true
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }

    @Test
    @DisplayName("Missing or dev-default QR signing salt fails fast in production")
    void devDefaultQrSaltFailsFast() {
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                "prod-ultra-strong-jwt-signing-secret-key-2026-xyz",
                ProductionConfigurationValidator.DEFAULT_DEV_QR_SALT,
                validRazorpay(),
                List.of(),
                "https://app.dineflow.example.com",
                true
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qr.salt");
    }

    @Test
    @DisplayName("Wildcard CORS origin, insecure cookies, mock provider, and placeholder Razorpay keys all fail fast")
    void insecureProdSettingsFailFast() {
        // Wildcard CORS
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                "prod-ultra-strong-jwt-signing-secret-key-2026-xyz",
                "prod-qr-signing-salt-2026-abc",
                validRazorpay(), List.of(), "*", true
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("cors.allowed-origins");

        // Insecure cookie
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                "prod-ultra-strong-jwt-signing-secret-key-2026-xyz",
                "prod-qr-signing-salt-2026-abc",
                validRazorpay(), List.of(), "https://app.dineflow.example.com", false
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("cookie.secure");

        // Mock provider active
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                "prod-ultra-strong-jwt-signing-secret-key-2026-xyz",
                "prod-qr-signing-salt-2026-abc",
                validRazorpay(), List.of(new MockPaymentGatewayProvider()), "https://app.dineflow.example.com", true
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("MockPaymentGatewayProvider");

        // Placeholder Razorpay secrets
        RazorpayProperties placeholder = new RazorpayProperties();
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                "prod-ultra-strong-jwt-signing-secret-key-2026-xyz",
                "prod-qr-signing-salt-2026-abc",
                placeholder, List.of(), "https://app.dineflow.example.com", true
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("Razorpay");
    }

    @Test
    @DisplayName("Refresh token cookie enforces HttpOnly, Secure, SameSite=Strict, and /api/v1/auth path")
    void refreshCookieSecurityAttributesVerified() {
        ResponseCookie cookie = cookieHelper.createRefreshTokenCookie("sample-rt", 3600);
        assertThat(cookie.getName()).isEqualTo("__Host-DineFlow-RT");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    }

    @Test
    @DisplayName("Actuator health is accessible while Prometheus metrics endpoint blocks unauthenticated callers")
    void actuatorAccessRestrictionsVerified() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }
}
