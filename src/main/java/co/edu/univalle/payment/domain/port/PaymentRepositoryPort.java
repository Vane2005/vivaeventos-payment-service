package co.edu.univalle.payment.domain.port;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByGatewayReference(String gatewayReference);

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    Optional<Payment> findActiveByOrderId(UUID orderId);

    List<Payment> findByOrderIdsAndStatus(List<UUID> orderIds, PaymentStatus status);

    boolean existsByOrderIdAndStatusIn(UUID orderId, PaymentStatus... statuses);
}
