package co.edu.univalle.payment.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vivaeventos.messaging")
public record MessagingProperties(
        String exchange,
        RoutingKeys routingKey
) {
    public record RoutingKeys(String approved, String failed) {}
}
