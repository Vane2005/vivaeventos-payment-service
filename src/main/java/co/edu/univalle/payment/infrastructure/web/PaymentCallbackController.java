package co.edu.univalle.payment.infrastructure.web;

import co.edu.univalle.payment.application.usecase.HandlePaymentCallbackUseCase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/callback")
public class PaymentCallbackController {

    private final HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;

    public PaymentCallbackController(HandlePaymentCallbackUseCase handlePaymentCallbackUseCase) {
        this.handlePaymentCallbackUseCase = handlePaymentCallbackUseCase;
    }

    @PostMapping("/wompi")
    public ResponseEntity<Void> wompiCallback(@RequestBody WompiWebhookPayload payload) {
        if (payload.data() != null && payload.data().transaction() != null) {
            var tx = payload.data().transaction();
            handlePaymentCallbackUseCase.executeByReference(
                    tx.reference(),
                    tx.status(),
                    tx.id()
            );
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmByTransaction(@RequestParam String transactionId) {
        handlePaymentCallbackUseCase.executeByTransactionId(transactionId);
        return ResponseEntity.ok().build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WompiWebhookPayload(WompiWebhookData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WompiWebhookData(WompiTransaction transaction) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WompiTransaction(
            String id,
            String status,
            String reference
    ) {}
}
