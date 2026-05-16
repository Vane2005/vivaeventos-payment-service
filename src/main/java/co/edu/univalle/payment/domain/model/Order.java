package co.edu.univalle.payment.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Order(
        UUID id,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        String customerEmail
) {
    public boolean isPendingPayment() {
        return status == OrderStatus.PENDING;
    }
}
