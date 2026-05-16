package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.OrderServicePort;
import co.edu.univalle.payment.domain.port.PaymentEventPublisherPort;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ApprovePaymentUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final OrderServicePort orderService;
    private final PaymentEventPublisherPort eventPublisher;

    public ApprovePaymentUseCase(
            PaymentRepositoryPort paymentRepository,
            OrderServicePort orderService,
            PaymentEventPublisherPort eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Payment execute(Payment payment) {
        if (payment.status() == PaymentStatus.APROBADO) {
            return payment;
        }

        var now = Instant.now();
        var approved = payment.withStatus(PaymentStatus.APROBADO, now);
        approved = paymentRepository.save(approved);

        orderService.markPaymentApproved(payment.orderId());
        eventPublisher.publishPaymentApproved(approved);

        return approved;
    }
}
