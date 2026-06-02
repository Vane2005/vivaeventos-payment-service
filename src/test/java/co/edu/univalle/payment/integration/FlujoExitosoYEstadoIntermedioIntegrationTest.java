package co.edu.univalle.payment.integration;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.infrastructure.persistence.PaymentJpaRepository;
import co.edu.univalle.payment.testsupport.StripeMockServer;
import lombok.SneakyThrows;
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
@DisplayName("SCUM-36: Pruebas de integración - Flujo exitoso y estado intermedio")
public class FlujoExitosoYEstadoIntermedioIntegrationTest {

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

    @SneakyThrows
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
        paymentJpaRepository.deleteAll();
    }


    @Test
    @DisplayName("SCUM-36.1 - Pago exitoso directo (APPROVED sin callback)")
    void flujoExitoso_pagoAprobadoDirectamente() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 100000.00))
                .addHeader("Content-Type", "application/json"));
        orderServiceMock.enqueue(new MockResponse().setResponseCode(200));

        stripeMock.stubCreateSessionPaid();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.APROBADO);
        assertThat(response.getBody().gatewayTransactionId()).isNotNull();

        var payment = paymentJpaRepository.findByGatewayReference(response.getBody().gatewayReference()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APROBADO);
        assertThat(payment.getPaidAt()).isNotNull();

        verify(rabbitTemplate, timeout(5000)).convertAndSend(
                eq(exchange),
                eq(approvedRoutingKey),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("SCUM-36.2 - Pago exitoso con callback (PENDING → APPROVED)")
    void flujoExitoso_pagoPendienteLuegoAprobadoPorCallback() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 80000.00))
                .addHeader("Content-Type", "application/json"));
        orderServiceMock.enqueue(new MockResponse().setResponseCode(200));

        stripeMock.stubCreateSessionPending();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var initiateResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        assertThat(initiateResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(initiateResponse.getBody().status()).isEqualTo(PaymentStatus.EN_PROCESO);

        var reference = initiateResponse.getBody().gatewayReference();
        var sessionId = initiateResponse.getBody().gatewayTransactionId();

        var callbackResponse = restTemplate.postForEntity(
                "/api/v1/payments/callback/stripe",
                new HttpEntity<>(
                        StripeMockServer.checkoutSessionCompletedWebhook(reference, sessionId),
                        headers
                ),
                String.class
        );

        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APROBADO);
            assertThat(payment.getPaidAt()).isNotNull();
        });

        verify(rabbitTemplate, timeout(5000)).convertAndSend(
                eq(exchange),
                eq(approvedRoutingKey),
                any(Object.class)
        );
    }



    @Test
    @DisplayName("SCUM-36.3 - Estado intermedio: Pago queda PENDIENTE esperando callback")
    void estadoIntermedio_pagoQuedaPendienteEsperandoCallback() throws Exception {
        var orderId = UUID.randomUUID();

        // Capturar el contador ANTES de ejecutar el test
        int requestsAntes = orderServiceMock.getRequestCount();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 95000.00))
                .addHeader("Content-Type", "application/json"));

        stripeMock.stubCreateSessionPending();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("{\"orderId\": \"" + orderId + "\"}", headers),
                PaymentApiResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.EN_PROCESO);

        var reference = response.getBody().gatewayReference();

        var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EN_PROCESO);
        assertThat(payment.getPaidAt()).isNull();


        assertThat(orderServiceMock.getRequestCount() - requestsAntes).isEqualTo(1);
    }

    @Test
    @DisplayName("SCUM-36.4 - Estado intermedio: consulta en pasarela PENDING no cambia estado")
    void estadoIntermedio_callbackPending_noCambiaEstado() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 110000.00))
                .addHeader("Content-Type", "application/json"));

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
                "/api/v1/payments/callback/confirm?transactionId=" + sessionId,
                HttpEntity.EMPTY,
                Void.class
        );

        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EN_PROCESO);
        assertThat(payment.getPaidAt()).isNull();

        verify(rabbitTemplate, never()).convertAndSend(
                eq(exchange),
                eq(approvedRoutingKey),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("SCUM-36.5 - Resolución por callback: PENDING → APPROVED")
    void resolucionCallback_pendingToApproved() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody(orderMockResponse(orderId, "PENDING", 75000.00))
                .addHeader("Content-Type", "application/json"));
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

        var callbackResponse = restTemplate.postForEntity(
                "/api/v1/payments/callback/stripe",
                new HttpEntity<>(
                        StripeMockServer.checkoutSessionCompletedWebhook(reference, sessionId),
                        headers
                ),
                String.class
        );

        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APROBADO);
            assertThat(payment.getPaidAt()).isNotNull();
        });

        verify(rabbitTemplate, timeout(5000)).convertAndSend(
                (String) eq(exchange),
                eq(approvedRoutingKey),
                any(Object.class)
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