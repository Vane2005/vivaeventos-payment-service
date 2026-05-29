package co.edu.univalle.payment.infrastructure.persistence;

import co.edu.univalle.payment.domain.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByGatewayReference(String gatewayReference);

    Optional<PaymentEntity> findByGatewayTransactionId(String gatewayTransactionId);

    Optional<PaymentEntity> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
            UUID orderId,
            Collection<PaymentStatus> statuses
    );


    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);
    List<PaymentEntity> findByOrderIdInAndStatus(List<UUID> orderIds, PaymentStatus status);


}
