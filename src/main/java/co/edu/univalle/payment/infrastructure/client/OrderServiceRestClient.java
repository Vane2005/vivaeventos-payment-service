package co.edu.univalle.payment.infrastructure.client;

import co.edu.univalle.payment.domain.exception.InvalidOrderStateException;
import co.edu.univalle.payment.domain.model.Order;
import co.edu.univalle.payment.domain.model.OrderStatus;
import co.edu.univalle.payment.domain.port.OrderServicePort;
import co.edu.univalle.payment.infrastructure.messaging.EventCancelledConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OrderServiceRestClient implements OrderServicePort {

    private final RestClient restClient;
    private static final Logger log = LoggerFactory.getLogger(OrderServiceRestClient.class);
    public OrderServiceRestClient(
            @Value("${services.order-service.url}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Order getOrder(UUID orderId) {
        try {
            var response = restClient.get()
                    .uri("/api/v1/orders/{orderId}", orderId)
                    .retrieve()
                    .body(OrderResponse.class);

            if (response == null) {
                throw new InvalidOrderStateException("Orden no encontrada: " + orderId);
            }

            validateOrderData(response, orderId);

            return new Order(
                response.id(),
                parseStatus(response.status()),
                response.totalPrice(),
                response.currency(),
                response.customerEmail()
            );
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new InvalidOrderStateException("Orden no encontrada: " + orderId);
            }
            throw new InvalidOrderStateException("Error al consultar la orden: " + ex.getMessage());
        }
    }

    @Override
    public void markPaymentApproved(UUID orderId) {
        restClient.patch()
                .uri("/api/v1/orders/{orderId}/confirm", orderId)
                .retrieve()
                .toBodilessEntity();
    }

    private void validateOrderData(OrderResponse response, UUID orderId) {
        if (response.customerEmail() == null || response.customerEmail().isBlank()) {
            throw new InvalidOrderStateException(
                    "La orden " + orderId + " no incluye email del cliente"
            );
        }
        if (response.currency() == null || response.currency().isBlank()) {
            throw new InvalidOrderStateException(
                    "La orden " + orderId + " no incluye moneda"
            );
        }
    }
    @Override
    public List<UUID> getOrderIdsByEvent(UUID eventId) {
        try {
            var response = restClient.get()
                    .uri("/api/v1/orders/by-event/{eventId}", eventId)
                    .retrieve()
                    .body(OrderIdListResponse.class);
            return response != null ? response.orderIds() : List.of();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return List.of();
            }
            log.error("Error consultando órdenes por evento: {}", ex.getMessage());
            return List.of();
        }
    }

    record OrderIdListResponse(List<UUID> orderIds) {}

    private OrderStatus parseStatus(String status) {
        if (status == null) {
            throw new InvalidOrderStateException("La orden no tiene estado definido");
        }
        return OrderStatus.valueOf(status.toUpperCase());
    }

    record OrderResponse(
        UUID id,
        String status,
        BigDecimal totalPrice,
        String customerEmail,
        String currency
    ) {}
}
