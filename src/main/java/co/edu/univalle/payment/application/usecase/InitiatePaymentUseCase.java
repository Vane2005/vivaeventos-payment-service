package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.application.dto.PaymentResponse;
import co.edu.univalle.payment.domain.exception.DuplicatePaymentException;
import co.edu.univalle.payment.domain.exception.InvalidOrderStateException;
import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.OrderServicePort;
import co.edu.univalle.payment.domain.port.PaymentGatewayPort;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
public class InitiatePaymentUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final OrderServicePort orderService;
    private final PaymentGatewayPort paymentGateway;
    private final PreventDuplicateChargeUseCase preventDuplicateChargeUseCase;
    private final RabbitTemplate rabbitTemplate;

    @Value("${vivaeventos.messaging.exchange}")
    private String exchange;

    @Value("${vivaeventos.messaging.routing-key.approved}")
    private String approvedRoutingKey;

    public InitiatePaymentUseCase(
            PaymentRepositoryPort paymentRepository,
            OrderServicePort orderService,
            PaymentGatewayPort paymentGateway,
            PreventDuplicateChargeUseCase preventDuplicateChargeUseCase,
            RabbitTemplate rabbitTemplate
    ) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.paymentGateway = paymentGateway;
        this.preventDuplicateChargeUseCase = preventDuplicateChargeUseCase;
        this.rabbitTemplate = rabbitTemplate;

    }

    @Transactional
    public PaymentResponse execute(UUID orderId) {
        preventDuplicateChargeUseCase.validate(orderId);

        var order = orderService.getOrder(orderId);

        if (!order.isPendingPayment()) {
            throw new InvalidOrderStateException(
                    "Solo órdenes en estado PENDING pueden procesarse. Estado actual: " + order.status()
            );
        }

        var now = Instant.now();
        var reference = "VE-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 8);

        var payment = new Payment(
                UUID.randomUUID(),
                orderId,
                order.totalAmount(),
                order.currency(),
                PaymentStatus.PENDIENTE,
                reference,
                null,
                null,
                null,
                order.customerEmail(),
                now,
                now,
                null
        );

        payment = paymentRepository.save(payment);

        var checkout = paymentGateway.createCheckout(payment);

        var updated = payment.withGatewayData(
                checkout.transactionId(),
                checkout.checkoutUrl(),
                mapGatewayStatus(checkout.status()),
                Instant.now()
        );
        if (updated.status() == PaymentStatus.FALLIDO) {
            updated = updated.withFailure(
                    checkout.failureReason() != null
                            ? checkout.failureReason()
                            : "Pago rechazado por la pasarela",
                    Instant.now()
            );
        }
        updated = paymentRepository.save(updated);
        if (updated.status() == PaymentStatus.APROBADO) {
            orderService.markPaymentApproved(orderId);
            rabbitTemplate.convertAndSend(exchange, approvedRoutingKey, PaymentResponse.from(updated));
        }
        return PaymentResponse.from(updated);
    }

    private PaymentStatus mapGatewayStatus(String gatewayStatus) {
        if (gatewayStatus == null) {
            return PaymentStatus.EN_PROCESO;
        }
        return switch (gatewayStatus.toUpperCase()) {
            case "APPROVED", "APPROVED_TRANSACTION", "APROBADO" -> PaymentStatus.APROBADO;
            case "DECLINED", "ERROR", "VOIDED", "FALLIDO" -> PaymentStatus.FALLIDO;
            case "PENDING" -> PaymentStatus.EN_PROCESO;
            default -> PaymentStatus.EN_PROCESO;
        };
    }
}
