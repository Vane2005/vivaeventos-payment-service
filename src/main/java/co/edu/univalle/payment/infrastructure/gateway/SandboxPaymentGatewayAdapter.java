package co.edu.univalle.payment.infrastructure.gateway;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.port.GatewayRefundResult;
import co.edu.univalle.payment.domain.port.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pasarela para entornos de prueba sin credenciales Wompi (perfil test / desarrollo local).
 * Genera URLs de checkout simuladas; los tests de integración usan mocks del puerto.
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
                "APPROVED",
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
