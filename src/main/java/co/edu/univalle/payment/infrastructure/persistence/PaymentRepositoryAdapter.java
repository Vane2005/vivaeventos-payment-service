package co.edu.univalle.payment.infrastructure.persistence;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        return PaymentMapper.toDomain(jpaRepository.save(PaymentMapper.toEntity(payment)));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByGatewayReference(String gatewayReference) {
        return jpaRepository.findByGatewayReference(gatewayReference).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId) {
        return jpaRepository.findByGatewayTransactionId(gatewayTransactionId).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findActiveByOrderId(UUID orderId) {
        return jpaRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
                orderId,
                Arrays.asList(PaymentStatus.PENDIENTE, PaymentStatus.EN_PROCESO, PaymentStatus.APROBADO)
        ).map(PaymentMapper::toDomain);
    }

    @Override
    public boolean existsByOrderIdAndStatusIn(UUID orderId, PaymentStatus... statuses) {
        return jpaRepository.existsByOrderIdAndStatusIn(orderId, Arrays.asList(statuses));
    }
}
