package co.edu.univalle.payment.infrastructure.gateway;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.port.GatewayRefundResult;
import co.edu.univalle.payment.domain.port.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pasarela simulada sin credenciales Stripe (perfil test / desarrollo local sin .env).
 * Devuelve checkout pendiente; la confirmación se hace vía callback o webhook de prueba.
 */
@Component
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "sandbox")
public class SandboxPaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public GatewayCheckoutResult createCheckout(Payment payment) {
        var txId = "sandbox-tx-" + UUID.randomUUID();
        var url = "sandbox-local";

        return new GatewayCheckoutResult(
                txId,
                url,
                "PENDING",
                null
        );
    }

    @Override
    public GatewayTransactionStatus queryTransactionStatus(String gatewayTransactionId) {
        return new GatewayTransactionStatus(
                gatewayTransactionId,
                "APPROVED",
                "Pago aprobado en sandbox"
        );
    }

    @Override
    public GatewayRefundResult refund(Payment payment, String reason) {
        return new GatewayRefundResult("sandbox-refund-" + UUID.randomUUID(), "REFUNDED");
    }
}
