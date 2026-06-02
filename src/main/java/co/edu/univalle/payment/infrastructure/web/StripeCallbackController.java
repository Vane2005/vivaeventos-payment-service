package co.edu.univalle.payment.infrastructure.web;

import co.edu.univalle.payment.application.usecase.HandlePaymentCallbackUseCase;
import co.edu.univalle.payment.infrastructure.gateway.StripeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Redirecciones de retorno desde Stripe Checkout.
 * Confirma el pago consultando la sesión y redirige al frontend.
 */
@RestController
@RequestMapping("/api/v1/payments/callback")
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "stripe")
public class StripeCallbackController {

    private final HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    private final StripeProperties stripeProperties;

    public StripeCallbackController(
            HandlePaymentCallbackUseCase handlePaymentCallbackUseCase,
            StripeProperties stripeProperties
    ) {
        this.handlePaymentCallbackUseCase = handlePaymentCallbackUseCase;
        this.stripeProperties = stripeProperties;
    }

    @GetMapping("/success")
    public ResponseEntity<Void> handleSuccess(@RequestParam("session_id") String sessionId) {
        handlePaymentCallbackUseCase.executeByTransactionId(sessionId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(resolveReturnUrl(sessionId)))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @GetMapping("/cancel")
    public ResponseEntity<Void> handleCancel() {
        var destination = stripeProperties.cancelReturnUrl();
        if (destination == null || destination.isBlank()) {
            destination = "http://localhost:3000/payment/cancel";
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(destination))
                .build();
    }

    private String resolveReturnUrl(String sessionId) {
        var template = stripeProperties.returnUrl();
        if (template == null || template.isBlank()) {
            return "http://localhost:3000/payment/success?session_id=" + sessionId;
        }
        return template.replace("{CHECKOUT_SESSION_ID}", sessionId);
    }
}
