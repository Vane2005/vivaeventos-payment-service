package co.edu.univalle.payment.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentApprovedMessage(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String gatewayReference,
        String customerEmail,
        Instant paidAt
) {}
