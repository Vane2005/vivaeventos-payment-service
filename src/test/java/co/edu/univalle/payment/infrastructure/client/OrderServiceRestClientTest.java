package co.edu.univalle.payment.infrastructure.client;

import co.edu.univalle.payment.domain.exception.InvalidOrderStateException;
import co.edu.univalle.payment.domain.model.OrderStatus;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceRestClientTest {

    MockWebServer mockWebServer;
    OrderServiceRestClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        var baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        client = new OrderServiceRestClient(baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getOrder_mapsCustomerEmailAndCurrencyFromOrderService() {
        var orderId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "id": "%s",
                          "status": "PENDING",
                          "totalPrice": 120000.00,
                          "customerEmail": "cliente@univalle.edu.co",
                          "currency": "COP"
                        }
                        """.formatted(orderId))
                .addHeader("Content-Type", "application/json"));

        var order = client.getOrder(orderId);

        assertThat(order.id()).isEqualTo(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.totalAmount()).isEqualByComparingTo("120000.00");
        assertThat(order.customerEmail()).isEqualTo("cliente@univalle.edu.co");
        assertThat(order.currency()).isEqualTo("COP");
    }

    @Test
    void getOrder_whenNotFound_throwsInvalidOrderStateException() {
        var orderId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> client.getOrder(orderId))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Orden no encontrada");
    }

    @Test
    void getOrder_whenCustomerEmailMissing_throwsInvalidOrderStateException() {
        var orderId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "id": "%s",
                          "status": "PENDING",
                          "totalPrice": 50000.00,
                          "currency": "COP"
                        }
                        """.formatted(orderId))
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.getOrder(orderId))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("email del cliente");
    }

    @Test
    void getOrder_whenCurrencyMissing_throwsInvalidOrderStateException() {
        var orderId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "id": "%s",
                          "status": "PENDING",
                          "totalPrice": 50000.00,
                          "customerEmail": "a@b.com"
                        }
                        """.formatted(orderId))
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.getOrder(orderId))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("moneda");
    }

    @Test
    void markPaymentApproved_callsConfirmEndpoint() throws InterruptedException {
        var orderId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        client.markPaymentApproved(orderId);

        var request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PATCH");
        assertThat(request.getPath()).isEqualTo("/api/v1/orders/" + orderId + "/confirm");
    }
}
