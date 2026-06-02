package co.edu.univalle.payment.integration;

import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.infrastructure.persistence.PaymentJpaRepository;
import co.edu.univalle.payment.testsupport.StripeMockServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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

    static MockWebServer orderServiceMock;
    static StripeMockServer stripeMock;


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

        stripeMock = new StripeMockServer();
        stripeMock.start();
    }

    @AfterAll
    static void stopMocks() throws IOException {
        orderServiceMock.shutdown();
        stripeMock.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.order-service.url",
                () -> orderServiceMock.url("/").toString().replaceAll("/$", ""));
        registry.add("payment.gateway.provider", () -> "stripe");
        registry.add("stripe.secret-key", () -> "sk_test_mock");
        registry.add("stripe.api-base", () -> stripeMock.getBaseUrl());
        registry.add("stripe.success-url", () -> "http://localhost:3000/success");
        registry.add("stripe.cancel-url", () -> "http://localhost:3000/cancel");
        registry.add("stripe.webhook-secret", () -> "");
    }

    @BeforeEach
    void setUp() {
        stripeMock.reset();
        clearInvocations(rabbitTemplate);
    }

    @Test
    @DisplayName("SCUM-41.1 - Pago rechazado por DECLINED")
    void flujoFallido_pagoDeclinado() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 150000.00))
                .setHeader("Content-Type", "application/json"));

        stripeMock.stubCreateSessionDeclined();

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

        stripeMock.stubCreateSessionError();

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

        stripeMock.stubCreateSessionPending();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var initiateResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        var reference = initiateResponse.getBody().gatewayReference();
        var sessionId = initiateResponse.getBody().gatewayTransactionId();

        var callbackResponse = restTemplate.postForEntity(
                "/api/v1/payments/callback/stripe",
                new HttpEntity<>(
                        StripeMockServer.checkoutSessionExpiredWebhook(reference, sessionId),
                        headers
                ),
                String.class
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

        stripeMock.stubCreateSessionPending();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var initiateResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        var reference = initiateResponse.getBody().gatewayReference();
        var sessionId = initiateResponse.getBody().gatewayTransactionId();

        var callbackResponse = restTemplate.postForEntity(
                "/api/v1/payments/callback/stripe",
                new HttpEntity<>(
                        StripeMockServer.checkoutSessionExpiredWebhook(reference, sessionId),
                        headers
                ),
                String.class
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

        stripeMock.stubCreateSessionPending();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var initiateResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        var reference = initiateResponse.getBody().gatewayReference();
        var sessionId = initiateResponse.getBody().gatewayTransactionId();
        stripeMock.setSessionMode(StripeMockServer.SessionStubMode.PAID);

        restTemplate.postForEntity(
                "/api/v1/payments/callback/stripe",
                new HttpEntity<>(
                        StripeMockServer.checkoutSessionCompletedWebhook(reference, sessionId),
                        headers
                ),
                String.class
        );

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APROBADO);
        });


        restTemplate.postForEntity(
                "/api/v1/payments/callback/stripe",
                new HttpEntity<>(
                        StripeMockServer.checkoutSessionCompletedWebhook(reference, sessionId),
                        headers
                ),
                String.class
        );

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(exchange),
                eq(approvedRoutingKey),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("SCUM-41.6 - Error 401 de autenticación con Stripe")
    void errorAutenticacionStripe_fallback() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 100000.00))
                .setHeader("Content-Type", "application/json"));

        stripeMock.stubCreateSessionUnauthorized();

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