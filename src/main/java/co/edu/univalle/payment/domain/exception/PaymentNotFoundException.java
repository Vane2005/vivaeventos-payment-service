package co.edu.univalle.payment.domain.exception;

import java.util.UUID;

public class PaymentNotFoundException extends PaymentDomainException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Pago no encontrado: " + paymentId);
    }

    public PaymentNotFoundException(String reference) {
        super("Pago no encontrado con referencia: " + reference);
    }
}
