package co.edu.univalle.payment.domain.port;

import co.edu.univalle.payment.domain.model.Payment;

public interface PaymentEventPublisherPort {

    void publishPaymentApproved(Payment payment);

    void publishPaymentFailed(Payment payment, String reason);
}
