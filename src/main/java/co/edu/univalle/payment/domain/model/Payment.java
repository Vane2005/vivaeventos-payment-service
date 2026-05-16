package co.edu.univalle.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String gatewayReference,
        String gatewayTransactionId,
        String checkoutUrl,
        String failureReason,
        String customerEmail,
        Instant createdAt,
        Instant updatedAt,
        Instant paidAt
) {
    public Payment withStatus(PaymentStatus newStatus, Instant now) {
        return new Payment(
                id, orderId, amount, currency, newStatus,
                gatewayReference, gatewayTransactionId, checkoutUrl, failureReason,
                customerEmail, createdAt, now,
                newStatus == PaymentStatus.APROBADO ? now : paidAt
        );
    }

    public Payment withGatewayData(String transactionId, String checkoutUrl, PaymentStatus status, Instant now) {
        return new Payment(
                id, orderId, amount, currency, status,
                gatewayReference, transactionId, checkoutUrl, failureReason,
                customerEmail, createdAt, now, paidAt
        );
    }

    public Payment withFailure(String reason, Instant now) {
        return new Payment(
                id, orderId, amount, currency, PaymentStatus.FALLIDO,
                gatewayReference, gatewayTransactionId, checkoutUrl, reason,
                customerEmail, createdAt, now, paidAt
        );
    }
}
