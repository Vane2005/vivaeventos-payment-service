package co.edu.univalle.payment.domain.port;

import co.edu.univalle.payment.domain.model.Order;

import java.util.UUID;

public interface OrderServicePort {

    Order getOrder(UUID orderId);

    void markPaymentApproved(UUID orderId);
}
