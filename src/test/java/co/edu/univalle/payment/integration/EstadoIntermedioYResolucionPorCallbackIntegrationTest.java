package co.edu.univalle.payment.integration;

import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.infrastructure.persistence.PaymentJpaRepository;
import lombok.Getter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = co.edu.univalle.vivaeventospaymentservice.VivaeventosPaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@DisplayName("SCUM-41: Pruebas de integración - Flujo fallido y callback")
public class EstadoIntermedioYResolucionPorCallbackIntegrationTest {

    static class WompiMockServer {
        private final MockWebServer mockWebServer;
        @Getter
        private String lastTransactionId;
        private String lastReference;

        public WompiMockServer() {
            this.mockWebServer = new MockWebServer();
        }

        public void start() throws IOException {
            mockWebServer.start();
        }

        public void shutdown() throws IOException {
            mockWebServer.shutdown();
        }


        public void drainRequests() throws InterruptedException {
            while (mockWebServer.getRequestCount() > 0) {
                mockWebServer.takeRequest();
            }
        }

        public String getBaseUrl() {
            return mockWebServer.url("/").toString().replaceAll("/$", "");
        }

        public int getRequestCount() {
            return mockWebServer.getRequestCount();
        }

        public RecordedRequest takeRequest() throws InterruptedException {
            return mockWebServer.takeRequest();
        }

        public void stubCreateTransactionPending() {
            this.lastTransactionId = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String responseBody = String.format(java.util.Locale.US,"""
                    {
                      "data": {
                        "id": "%s",
                        "status": "PENDING",
                        "redirect_url": "https://checkout.wompi.co/pay/%s",
                        "created_at": "2024-01-15T10:00:00.000Z"
                      }
                    }
                    """, lastTransactionId, lastTransactionId);
            enqueueSuccess(responseBody);
        }

        public void stubCreateTransactionDeclined() {
            this.lastTransactionId = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String responseBody = String.format(java.util.Locale.US,"""
                    {
                      "data": {
                        "id": "%s",
                        "status": "DECLINED",
                        "error_message": "Tarjeta rechazada por el banco",
                        "created_at": "2024-01-15T10:00:00.000Z"
                      }
                    }
                    """, lastTransactionId);
            enqueueSuccess(responseBody);
        }

        public void stubCreateTransactionError() {
            this.lastTransactionId = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String responseBody = String.format(java.util.Locale.US,"""
                    {
                      "data": {
                        "id": "%s",
                        "status": "ERROR",
                        "error_message": "Error interno en la pasarela",
                        "created_at": "2024-01-15T10:00:00.000Z"
                      }
                    }
                    """, lastTransactionId);
            enqueueSuccess(responseBody);
        }

        public void stubCreateTransactionUnauthorized() {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("""
                            {
                              "error": {
                                "type": "authentication_error",
                                "reason": "Invalid API key"
                              }
                            }
                            """)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        }

        public void stubGetTransactionNotFound(String transactionId) {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setBody("""
                            {
                              "error": {
                                "type": "not_found",
                                "reason": "Transaction not found"
                              }
                            }
                            """)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        }

        private void enqueueSuccess(String body) {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(body)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        }

        private String orderMockResponse(UUID orderId, String status, double amount) {
            return String.format(java.util.Locale.US,
                    """
                    {
                      "id": "%s",
                      "status": "%s",
                      "totalPrice": %.2f,
                      "customerEmail": "cliente@test.com",
                      "currency": "COP"
                    }
                    """, orderId, status, amount);
         }


    }

    static MockWebServer orderServiceMock;
    static WompiMockServer wompiMock;


    @MockBean
    RabbitTemplate rabbitTemplate;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PaymentJpaRepository paymentJpaRepository;

    @Value("${vivaeventos.messaging.exchange}")
    String exchange;

    @Value("${vivaeventos.messaging.routing-key.approved}")
    String approvedRoutingKey;

    @BeforeAll
    static void startMocks() throws IOException {
        orderServiceMock = new MockWebServer();
        orderServiceMock.start();

        wompiMock = new WompiMockServer();
        wompiMock.start();
    }

    @AfterAll
    static void stopMocks() throws IOException {
        orderServiceMock.shutdown();
        wompiMock.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.order-service.url",
                () -> orderServiceMock.url("/").toString().replaceAll("/$", ""));


        registry.add("payment.gateway.provider", () -> "wompi");

        registry.add("wompi.base-url", () -> wompiMock.getBaseUrl());
        registry.add("wompi.private-key", () -> "prv_test_mock");
        registry.add("wompi.public-key", () -> "pub_test_mock");
        registry.add("wompi.redirect-url", () -> "https://vivaeventos.com/checkout/redirect");
        registry.add("wompi.callback-url", () -> "https://vivaeventos.com/api/v1/payments/callback/wompi");
    }

    @BeforeEach
    void setUp() {

        clearInvocations(rabbitTemplate);
    }

    @Test
    @DisplayName("SCUM-41.1 - Pago rechazado por DECLINED")
    void flujoFallido_pagoDeclinado() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 150000.00))
                .setHeader("Content-Type", "application/json"));

        wompiMock.stubCreateTransactionDeclined();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.FALLIDO);
        assertThat(response.getBody().failureReason()).isNotBlank();

        var payment = paymentJpaRepository
                .findByGatewayReference(response.getBody().gatewayReference())
                .orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FALLIDO);
        assertThat(payment.getPaidAt()).isNull();

        verify(rabbitTemplate, never()).convertAndSend(
                eq(exchange),
                eq(approvedRoutingKey),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("SCUM-41.2 - Error en pasarela (ERROR)")
    void flujoFallido_errorEnPasarela() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 120000.00))
                .setHeader("Content-Type", "application/json"));

        wompiMock.stubCreateTransactionError();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.FALLIDO);

        var payment = paymentJpaRepository.findByGatewayReference(response.getBody().gatewayReference()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FALLIDO);
    }

    @Test
    @DisplayName("SCUM-41.3 - Callback con DECLINED cambia a FALLIDO")
    void callbackDeclined_cambiaEstadoAFallido() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 65000.00))
                .setHeader("Content-Type", "application/json"));

        wompiMock.stubCreateTransactionPending();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var initiateResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        var reference = initiateResponse.getBody().gatewayReference();
        var transactionId = wompiMock.getLastTransactionId();

        var callbackResponse = restTemplate.postForEntity(
                "/api/v1/payments/callback/wompi",
                new HttpEntity<>(wompiCallbackPayload(reference, transactionId, "DECLINED"), headers),
                Void.class
        );

        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FALLIDO);
            assertThat(payment.getFailureReason()).isNotBlank();
        });

        verify(rabbitTemplate, never()).convertAndSend(
                eq(exchange),
                eq(approvedRoutingKey),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("SCUM-41.4 - Callback con ERROR cambia a FALLIDO")
    void callbackError_cambiaEstadoAFallido() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 70000.00))
                .setHeader("Content-Type", "application/json"));

        wompiMock.stubCreateTransactionPending();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var initiateResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        var reference = initiateResponse.getBody().gatewayReference();
        var transactionId = wompiMock.getLastTransactionId();

        var callbackResponse = restTemplate.postForEntity(
                "/api/v1/payments/callback/wompi",
                new HttpEntity<>(wompiCallbackPayload(reference, transactionId, "ERROR"), headers),
                Void.class
        );

        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FALLIDO);
        });
    }

    @Test
    @DisplayName("SCUM-41.5 - Callback duplicado no procesa dos veces")
    void callbackDuplicado_noProcesaDosVeces() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 90000.00))
                .setHeader("Content-Type", "application/json"));
        orderServiceMock.enqueue(new MockResponse().setResponseCode(200));

        wompiMock.stubCreateTransactionPending();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var initiateResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        var reference = initiateResponse.getBody().gatewayReference();
        var transactionId = wompiMock.getLastTransactionId();


        restTemplate.postForEntity(
                "/api/v1/payments/callback/wompi",
                new HttpEntity<>(wompiCallbackPayload(reference, transactionId, "APPROVED"), headers),
                Void.class
        );

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APROBADO);
        });


        restTemplate.postForEntity(
                "/api/v1/payments/callback/wompi",
                new HttpEntity<>(wompiCallbackPayload(reference, transactionId, "APPROVED"), headers),
                Void.class
        );

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(exchange),
                eq(approvedRoutingKey),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("SCUM-41.6 - Error 401 de autenticación con Wompi")
    void errorAutenticacionWompi_fallback() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 100000.00))
                .setHeader("Content-Type", "application/json"));

        wompiMock.stubCreateTransactionUnauthorized();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);


        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> restTemplate.postForEntity(
                        "/api/v1/payments",
                        new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                        PaymentApiResponse.class
                )
        );
    }

    private String orderMockResponse(UUID orderId, String status, double amount) {
        return String.format(java.util.Locale.US,"""
                {
                  "id": "%s",
                  "status": "%s",
                  "totalPrice": %.2f,
                  "customerEmail": "cliente@test.com",
                  "currency": "COP"
                }
                """, orderId, status, amount);
    }

    private String wompiCallbackPayload(String reference, String transactionId, String status) {
        return String.format(java.util.Locale.US,"""
                {
                  "data": {
                    "transaction": {
                      "id": "%s",
                      "status": "%s",
                      "reference": "%s"
                    }
                  }
                }
                """, transactionId, status, reference);
    }

    record PaymentApiResponse(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String gatewayReference,
            String gatewayTransactionId,
            String checkoutUrl,
            String failureReason
    ) {}
}