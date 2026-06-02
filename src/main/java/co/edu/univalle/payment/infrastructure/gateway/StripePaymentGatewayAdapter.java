package co.edu.univalle.payment.infrastructure.gateway;

import co.edu.univalle.payment.domain.exception.PaymentDomainException;
import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.port.GatewayRefundResult;
import co.edu.univalle.payment.domain.port.PaymentGatewayPort;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

@Component
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "stripe")
@EnableConfigurationProperties(StripeProperties.class)
public class StripePaymentGatewayAdapter implements PaymentGatewayPort {

    private final StripeProperties properties;

    public StripePaymentGatewayAdapter(StripeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void configureStripe() {
        if (properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new IllegalStateException(
                    "STRIPE_SECRET_KEY no configurada. Copia .env.example a .env y agrega tus claves de prueba de Stripe."
            );
        }
        Stripe.apiKey = properties.secretKey();
        if (properties.apiBase() != null && !properties.apiBase().isBlank()) {
            Stripe.overrideApiBase(properties.apiBase());
        }
    }

    @Override
    public GatewayCheckoutResult createCheckout(Payment payment) {
        try {
            var amountInMinorUnit = payment.amount()
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            var params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(resolveCustomerEmail(payment))
                    .setClientReferenceId(payment.gatewayReference())
                    .setSuccessUrl(properties.successUrl())
                    .setCancelUrl(properties.cancelUrl())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(payment.currency().toLowerCase())
                                                    .setUnitAmount(amountInMinorUnit)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Boletas VivaEventos - orden " + payment.orderId())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .putMetadata("orderId", payment.orderId().toString())
                    .putMetadata("paymentId", payment.id().toString())
                    .build();

            var session = Session.create(params);
            return new GatewayCheckoutResult(
                    session.getId(),
                    session.getUrl(),
                    mapSessionStatus(session),
                    null
            );
        } catch (StripeException ex) {
            throw new PaymentDomainException("Error al crear sesión de pago en Stripe: " + ex.getMessage());
        }
    }

    @Override
    public GatewayTransactionStatus queryTransactionStatus(String gatewayTransactionId) {
        try {
            var session = Session.retrieve(gatewayTransactionId);
            return new GatewayTransactionStatus(
                    session.getId(),
                    mapSessionStatus(session),
                    session.getStatus()
            );
        } catch (StripeException ex) {
            throw new PaymentDomainException("No se pudo consultar la sesión de Stripe: " + gatewayTransactionId);
        }
    }

    @Override
    public GatewayRefundResult refund(Payment payment, String reason) {
        try {
            var session = Session.retrieve(payment.gatewayTransactionId());
            var paymentIntentId = session.getPaymentIntent();
            if (paymentIntentId == null || paymentIntentId.isBlank()) {
                throw new PaymentDomainException("La sesión no tiene PaymentIntent asociado para reembolso");
            }

            var refund = Refund.create(
                    RefundCreateParams.builder()
                            .setPaymentIntent(paymentIntentId)
                            .putMetadata("reason", reason != null ? reason : "event_cancelled")
                            .build()
            );

            return new GatewayRefundResult(refund.getId(), refund.getStatus());
        } catch (StripeException ex) {
            throw new PaymentDomainException("Error al reembolsar en Stripe: " + ex.getMessage());
        }
    }

    private String resolveCustomerEmail(Payment payment) {
        if (payment.customerEmail() != null && !payment.customerEmail().isBlank()) {
            return payment.customerEmail();
        }
        throw new PaymentDomainException(
                "El pago requiere email del cliente (proviene de la orden vinculada al usuario registrado)"
        );
    }

    static String mapSessionStatus(Session session) {
        if ("complete".equalsIgnoreCase(session.getStatus())
                && "paid".equalsIgnoreCase(session.getPaymentStatus())) {
            return "APPROVED";
        }
        if ("expired".equalsIgnoreCase(session.getStatus())) {
            return "EXPIRED";
        }
        return "PENDING";
    }
}
