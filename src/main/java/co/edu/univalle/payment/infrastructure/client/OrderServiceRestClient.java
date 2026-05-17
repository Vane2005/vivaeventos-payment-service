package co.edu.univalle.payment.infrastructure.client;

import co.edu.univalle.payment.domain.exception.InvalidOrderStateException;
import co.edu.univalle.payment.domain.model.Order;
import co.edu.univalle.payment.domain.model.OrderStatus;
import co.edu.univalle.payment.domain.port.OrderServicePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class OrderServiceRestClient implements OrderServicePort {

    private final RestClient restClient;

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
