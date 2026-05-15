package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.PaymentEventPublisherPort;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class FailPaymentUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final PaymentEventPublisherPort eventPublisher;

    public FailPaymentUseCase(
            PaymentRepositoryPort paymentRepository,
            PaymentEventPublisherPort eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Payment execute(Payment payment, String reason) {
        if (payment.status() == PaymentStatus.FALLIDO) {
            return payment;
        }

        var failed = payment.withFailure(reason, Instant.now());
        failed = paymentRepository.save(failed);
        eventPublisher.publishPaymentFailed(failed, reason);
        return failed;
    }
}
