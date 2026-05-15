package co.edu.univalle.payment.infrastructure.messaging;

import java.util.UUID;

public record PaymentFailedMessage(
        UUID paymentId,
        UUID orderId,
        String customerEmail,
        String reason,
        String gatewayReference
) {}
