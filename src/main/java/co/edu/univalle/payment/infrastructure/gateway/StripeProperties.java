package co.edu.univalle.payment.infrastructure.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(
        String secretKey,
        String publishableKey,
        String webhookSecret,
        String successUrl,
        String cancelUrl,
        String returnUrl,
        String cancelReturnUrl,
        String apiBase
) {}
