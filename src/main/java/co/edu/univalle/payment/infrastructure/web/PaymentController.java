package co.edu.univalle.payment.infrastructure.web;

import co.edu.univalle.payment.application.dto.InitiatePaymentRequest;
import co.edu.univalle.payment.application.dto.PaymentResponse;
import co.edu.univalle.payment.application.usecase.GetPaymentUseCase;
import co.edu.univalle.payment.application.usecase.InitiatePaymentUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final InitiatePaymentUseCase initiatePaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;

    public PaymentController(
            InitiatePaymentUseCase initiatePaymentUseCase,
            GetPaymentUseCase getPaymentUseCase
    ) {
        this.initiatePaymentUseCase = initiatePaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse initiatePayment(@Valid @RequestBody InitiatePaymentRequest request) {
        return initiatePaymentUseCase.execute(request.orderId());
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(@PathVariable UUID paymentId) {
        return getPaymentUseCase.execute(paymentId);
    }
}
