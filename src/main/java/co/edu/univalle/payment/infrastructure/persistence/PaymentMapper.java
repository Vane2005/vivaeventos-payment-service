package co.edu.univalle.payment.infrastructure.persistence;

import co.edu.univalle.payment.domain.model.Payment;

final class PaymentMapper {

    private PaymentMapper() {}

    static Payment toDomain(PaymentEntity entity) {
        return new Payment(
                entity.getId(),
                entity.getOrderId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getGatewayReference(),
                entity.getGatewayTransactionId(),
                entity.getCheckoutUrl(),
                entity.getFailureReason(),
                entity.getCustomerEmail(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getPaidAt()
        );
    }

    static PaymentEntity toEntity(Payment payment) {
        var entity = new PaymentEntity();
        entity.setId(payment.id());
        entity.setOrderId(payment.orderId());
        entity.setAmount(payment.amount());
        entity.setCurrency(payment.currency());
        entity.setStatus(payment.status());
        entity.setGatewayReference(payment.gatewayReference());
        entity.setGatewayTransactionId(payment.gatewayTransactionId());
        entity.setCheckoutUrl(payment.checkoutUrl());
        entity.setFailureReason(payment.failureReason());
        entity.setCustomerEmail(payment.customerEmail());
        entity.setCreatedAt(payment.createdAt());
        entity.setUpdatedAt(payment.updatedAt());
        entity.setPaidAt(payment.paidAt());
        return entity;
    }
}
