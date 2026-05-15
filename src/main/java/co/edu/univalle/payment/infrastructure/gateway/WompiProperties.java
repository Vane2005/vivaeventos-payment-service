package co.edu.univalle.payment.infrastructure.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wompi")
public record WompiProperties(
        String baseUrl,
        String privateKey,
        String publicKey,
        String redirectUrl,
        String callbackUrl
) {}
