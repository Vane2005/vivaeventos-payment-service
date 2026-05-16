package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.application.dto.PaymentResponse;
import co.edu.univalle.payment.domain.exception.PaymentNotFoundException;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetPaymentUseCase {

    private final PaymentRepositoryPort paymentRepository;

    public GetPaymentUseCase(PaymentRepositoryPort paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public PaymentResponse execute(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
