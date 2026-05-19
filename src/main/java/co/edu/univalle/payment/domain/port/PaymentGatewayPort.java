package co.edu.univalle.payment.domain.port;

import co.edu.univalle.payment.domain.model.Payment;

public interface PaymentGatewayPort {

    GatewayCheckoutResult createCheckout(Payment payment);

    GatewayTransactionStatus queryTransactionStatus(String gatewayTransactionId);

    record GatewayCheckoutResult(
            String transactionId,
            String checkoutUrl,
            String status,
            String failureReason
    ) {}

    record GatewayTransactionStatus(
            String transactionId,
            String status,
            String statusMessage
    ) {}
}
