package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.exception.DuplicatePaymentException;
import co.edu.univalle.payment.domain.exception.PaymentDomainException;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PreventDuplicateChargeUseCase {

    private final PaymentRepositoryPort paymentRepository;

    public PreventDuplicateChargeUseCase(PaymentRepositoryPort paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Valida que no exista un pago previo en estado PENDIENTE, EN_PROCESO o APROBADO
     * para la misma orden.
     */
    public void validate(UUID orderId) {
        boolean hasActivePayment = paymentRepository.existsByOrderIdAndStatusIn(
                orderId,
                PaymentStatus.PENDIENTE,
                PaymentStatus.EN_PROCESO,
                PaymentStatus.APROBADO
        );

        if (hasActivePayment) {
            throw new DuplicatePaymentException(orderId);
        }
    }
}