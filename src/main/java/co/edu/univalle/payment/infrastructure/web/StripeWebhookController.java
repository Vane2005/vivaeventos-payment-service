package co.edu.univalle.payment.infrastructure.web;

import co.edu.univalle.payment.application.usecase.HandlePaymentCallbackUseCase;
import co.edu.univalle.payment.infrastructure.gateway.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/callback")
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    private final StripeProperties stripeProperties;

    public StripeWebhookController(
            HandlePaymentCallbackUseCase handlePaymentCallbackUseCase,
            StripeProperties stripeProperties
    ) {
        this.handlePaymentCallbackUseCase = handlePaymentCallbackUseCase;
        this.stripeProperties = stripeProperties;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        Event event = parseEvent(payload, signature);

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            case "checkout.session.expired" -> handleCheckoutSessionExpired(event);
            case "payment_intent.payment_failed" -> log.warn("payment_intent.payment_failed recibido: {}", event.getId());
            default -> log.debug("Evento Stripe ignorado: {}", event.getType());
        }

        return ResponseEntity.ok("ok");
    }

    private void handleCheckoutSessionCompleted(Event event) {
        var session = deserializeSession(event);
        if (session == null || session.getClientReferenceId() == null) {
            log.warn("checkout.session.completed sin client_reference_id");
            return;
        }

        var status = "paid".equalsIgnoreCase(session.getPaymentStatus()) ? "APPROVED" : "PENDING";
        handlePaymentCallbackUseCase.executeByReference(
                session.getClientReferenceId(),
                status,
                session.getId()
        );
    }

    private void handleCheckoutSessionExpired(Event event) {
        var session = deserializeSession(event);
        if (session == null || session.getClientReferenceId() == null) {
            return;
        }

        handlePaymentCallbackUseCase.executeByReference(
                session.getClientReferenceId(),
                "EXPIRED",
                session.getId()
        );
    }

    private Session deserializeSession(Event event) {
        return event.getDataObjectDeserializer()
                .getObject()
                .filter(Session.class::isInstance)
                .map(Session.class::cast)
                .orElse(null);
    }

    private Event parseEvent(String payload, String signature) {
        var webhookSecret = stripeProperties.webhookSecret();
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (signature == null || signature.isBlank()) {
                throw new IllegalArgumentException("Falta cabecera Stripe-Signature");
            }
            try {
                return Webhook.constructEvent(payload, signature, webhookSecret);
            } catch (SignatureVerificationException ex) {
                throw new IllegalArgumentException("Firma de webhook Stripe inválida", ex);
            }
        }

        log.warn("STRIPE_WEBHOOK_SECRET no configurado: se acepta el webhook sin verificar firma (solo desarrollo)");
        return ApiResource.GSON.fromJson(payload, Event.class);
    }
}
