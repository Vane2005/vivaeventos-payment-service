package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.application.dto.PaymentResponse;
import co.edu.univalle.payment.domain.exception.DuplicatePaymentException;
import co.edu.univalle.payment.domain.exception.InvalidOrderStateException;
import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.OrderServicePort;
import co.edu.univalle.payment.domain.port.PaymentGatewayPort;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InitiatePaymentUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final OrderServicePort orderService;
    private final PaymentGatewayPort paymentGateway;
    private final PreventDuplicateChargeUseCase preventDuplicateChargeUseCase;

    public InitiatePaymentUseCase(
            PaymentRepositoryPort paymentRepository,
            OrderServicePort orderService,
            PaymentGatewayPort paymentGateway,
            PreventDuplicateChargeUseCase preventDuplicateChargeUseCase
    ) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.paymentGateway = paymentGateway;
        this.preventDuplicateChargeUseCase = preventDuplicateChargeUseCase;

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

        updated = paymentRepository.save(updated);
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
