package co.edu.univalle.payment.infrastructure.web;

import co.edu.univalle.payment.infrastructure.gateway.StripeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/stripe")
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "stripe")
public class StripeConfigController {

    private final StripeProperties stripeProperties;

    public StripeConfigController(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @GetMapping("/config")
    public StripePublicConfig getConfig() {
        return new StripePublicConfig(stripeProperties.publishableKey());
    }

    record StripePublicConfig(String publishableKey) {}
}
