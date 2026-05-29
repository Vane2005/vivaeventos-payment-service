package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.OrderServicePort;
import co.edu.univalle.payment.domain.port.PaymentGatewayPort;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import co.edu.univalle.payment.infrastructure.messaging.EventCancelledMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessRefundUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessRefundUseCase.class);
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final OrderServicePort orderService;

    public ProcessRefundUseCase(PaymentRepositoryPort paymentRepository,
                                PaymentGatewayPort paymentGateway,
                                OrderServicePort orderService) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.orderService = orderService;
    }

    @Transactional
    public void execute(EventCancelledMessage message) {
        log.info("Procesando reembolsos para evento: {}", message.getEventId());

        List<UUID> orderIds = orderService.getOrderIdsByEvent(message.getEventId());

        if (orderIds.isEmpty()) {
            log.info("No hay órdenes para el evento {}", message.getEventId());
            return;
        }

        List<Payment> payments = paymentRepository.findByOrderIdsAndStatus(orderIds, PaymentStatus.APROBADO);

        if (payments.isEmpty()) {
            log.info("No hay pagos aprobados para reembolsar");
            return;
        }

        for (Payment payment : payments) {
            try {
                paymentGateway.refund(payment, message.getReason());
                Payment refunded = payment.withStatus(PaymentStatus.REEMBOLSADO, Instant.now());
                paymentRepository.save(refunded);
                log.info(" Reembolsado paymentId={}, orderId={}", payment.id(), payment.orderId());
            } catch (Exception e) {
                log.error(" Error reembolsando paymentId={}: {}", payment.id(), e.getMessage());
            }
        }
    }
}