package co.edu.univalle.payment.infrastructure.messaging;

import co.edu.univalle.payment.application.usecase.ProcessRefundUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EventCancelledConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventCancelledConsumer.class);
    private final ProcessRefundUseCase processRefundUseCase;

    public EventCancelledConsumer(ProcessRefundUseCase processRefundUseCase) {
        this.processRefundUseCase = processRefundUseCase;
    }

    @RabbitListener(queues = "evento.cancelado")
    public void consumeEventCancelled(EventCancelledMessage message) {
        log.info(" Evento cancelado recibido: eventId={}", message.getEventId());
        processRefundUseCase.execute(message);
    }
}