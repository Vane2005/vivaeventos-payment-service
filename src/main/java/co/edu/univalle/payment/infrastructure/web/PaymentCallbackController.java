package co.edu.univalle.payment.infrastructure.web;

import co.edu.univalle.payment.application.usecase.HandlePaymentCallbackUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/callback")
public class PaymentCallbackController {

    private final HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;

    public PaymentCallbackController(HandlePaymentCallbackUseCase handlePaymentCallbackUseCase) {
        this.handlePaymentCallbackUseCase = handlePaymentCallbackUseCase;
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmByTransaction(@RequestParam String transactionId) {
        handlePaymentCallbackUseCase.executeByTransactionId(transactionId);
        return ResponseEntity.ok().build();
    }
}
