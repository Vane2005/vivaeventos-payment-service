package co.edu.univalle.payment.domain.exception;

import java.util.UUID;

public class DuplicatePaymentException extends PaymentDomainException {

    public DuplicatePaymentException(UUID orderId) {
        super("Ya existe un pago activo o aprobado para la orden: " + orderId);
    }
}
