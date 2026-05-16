package co.edu.univalle.payment.application.dto;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String gatewayReference,
        String gatewayTransactionId,
        String checkoutUrl,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant paidAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.id(),
                payment.orderId(),
                payment.amount(),
                payment.currency(),
                payment.status(),
                payment.gatewayReference(),
                payment.gatewayTransactionId(),
                payment.checkoutUrl(),
                payment.failureReason(),
                payment.createdAt(),
                payment.updatedAt(),
                payment.paidAt()
        );
    }
}
