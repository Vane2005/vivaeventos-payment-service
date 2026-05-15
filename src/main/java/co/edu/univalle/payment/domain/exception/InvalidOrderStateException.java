package co.edu.univalle.payment.domain.exception;

public class InvalidOrderStateException extends PaymentDomainException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
