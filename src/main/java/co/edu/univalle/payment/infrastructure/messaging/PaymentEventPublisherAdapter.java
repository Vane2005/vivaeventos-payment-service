package co.edu.univalle.payment.infrastructure.messaging;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.port.PaymentEventPublisherPort;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisherAdapter implements PaymentEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    public PaymentEventPublisherAdapter(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publishPaymentApproved(Payment payment) {
        var message = new PaymentApprovedMessage(
                payment.id(),
                payment.orderId(),
                payment.amount(),
                payment.currency(),
                payment.gatewayReference(),
                payment.customerEmail(),
                payment.paidAt()
        );
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.routingKey().approved(),
                message
        );
    }

    @Override
    public void publishPaymentFailed(Payment payment, String reason) {
        var message = new PaymentFailedMessage(
                payment.id(),
                payment.orderId(),
                payment.customerEmail(),
                reason,
                payment.gatewayReference()
        );
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.routingKey().failed(),
                message
        );
    }
}
