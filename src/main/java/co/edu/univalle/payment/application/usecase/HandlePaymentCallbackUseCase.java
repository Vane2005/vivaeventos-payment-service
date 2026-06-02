package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.exception.PaymentNotFoundException;
import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.PaymentGatewayPort;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class HandlePaymentCallbackUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final FailPaymentUseCase failPaymentUseCase;

    public HandlePaymentCallbackUseCase(
            PaymentRepositoryPort paymentRepository,
            PaymentGatewayPort paymentGateway,
            ApprovePaymentUseCase approvePaymentUseCase,
            FailPaymentUseCase failPaymentUseCase
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.approvePaymentUseCase = approvePaymentUseCase;
        this.failPaymentUseCase = failPaymentUseCase;
    }

    @Transactional
    public void executeByReference(String gatewayReference, String gatewayStatus, String transactionId) {
        var payment = paymentRepository.findByGatewayReference(gatewayReference)
                .orElseThrow(() -> new PaymentNotFoundException(gatewayReference));

        if (payment.status() == PaymentStatus.APROBADO) {
            return;
        }

        processStatus(payment, gatewayStatus, transactionId, null);
    }

    @Transactional
    public void executeByTransactionId(String transactionId) {
        var gatewayStatus = paymentGateway.queryTransactionStatus(transactionId);

        var payment = paymentRepository.findByGatewayTransactionId(transactionId)
                .orElseThrow(() -> new PaymentNotFoundException(transactionId));

        if (payment.status() == PaymentStatus.APROBADO) {
            return;
        }

        processStatus(payment, gatewayStatus.status(), transactionId, gatewayStatus.statusMessage());
    }

    private void processStatus(Payment payment, String gatewayStatus, String transactionId, String message) {
        var normalized = gatewayStatus == null ? "" : gatewayStatus.toUpperCase();

        if (isApproved(normalized)) {
            var withTx = payment.gatewayTransactionId() == null && transactionId != null
                    ? payment.withGatewayData(transactionId, payment.checkoutUrl(), payment.status(), Instant.now())
                    : payment;
            approvePaymentUseCase.execute(withTx);
            return;
        }

        if (isFailed(normalized)) {
            failPaymentUseCase.execute(payment, message != null ? message : "Pago rechazado por la pasarela");
            return;
        }

        if (isPending(normalized)) {
            var updated = payment.withGatewayData(
                    transactionId != null ? transactionId : payment.gatewayTransactionId(),
                    payment.checkoutUrl(),
                    PaymentStatus.EN_PROCESO,
                    Instant.now()
            );
            paymentRepository.save(updated);
        }
    }

    private boolean isApproved(String status) {
        return status.contains("APPROVED") || status.equals("APROBADO")
                || status.equals("PAID") || status.equals("SUCCEEDED");
    }

    private boolean isFailed(String status) {
        return status.contains("DECLINED") || status.contains("ERROR")
                || status.contains("VOIDED") || status.equals("FALLIDO")
                || status.equals("EXPIRED") || status.equals("CANCELED") || status.equals("FAILED");
    }

    private boolean isPending(String status) {
        return status.contains("PENDING") || status.equals("OPEN") || status.equals("UNPAID")
                || status.isEmpty();
    }
}
