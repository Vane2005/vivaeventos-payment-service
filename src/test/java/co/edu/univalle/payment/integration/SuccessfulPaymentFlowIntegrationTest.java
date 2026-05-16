package co.edu.univalle.payment.integration;

import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.infrastructure.messaging.PaymentApprovedMessage;
import co.edu.univalle.payment.infrastructure.persistence.PaymentJpaRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = co.edu.univalle.vivaeventospaymentservice.VivaeventosPaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class SuccessfulPaymentFlowIntegrationTest {

    static MockWebServer orderServiceMock;

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
    static void startOrderMock() throws IOException {
        orderServiceMock = new MockWebServer();
        orderServiceMock.start();
    }

    @AfterAll
    static void stopOrderMock() throws IOException {
        orderServiceMock.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.order-service.url", () -> orderServiceMock.url("/").toString().replaceAll("/$", ""));
    }

    private static final String ORDER_CUSTOMER_EMAIL = "correo.real@ejemplo.com";
    private static final String ORDER_CURRENCY = "COP";

    @Test
    void approvedPayment_updatesOrderAndPublishesTicketEvent() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody("""
                        {
                          "id": "%s",
                          "status": "PENDING",
                          "totalPrice": 80000.00,
                          "customerEmail": "%s",
                          "currency": "%s"
                        }
                        """.formatted(orderId, ORDER_CUSTOMER_EMAIL, ORDER_CURRENCY))
                .addHeader("Content-Type", "application/json"));

        orderServiceMock.enqueue(new MockResponse().setResponseCode(200));

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var initiateResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>("""
                        {"orderId": "%s"}
                        """.formatted(orderId), headers),
                PaymentApiResponse.class
        );

        assertThat(initiateResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(initiateResponse.getBody().currency()).isEqualTo(ORDER_CURRENCY);
        var reference = initiateResponse.getBody().gatewayReference();

        var paymentAfterInitiate = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
        assertThat(paymentAfterInitiate.getCustomerEmail()).isEqualTo(ORDER_CUSTOMER_EMAIL);
        assertThat(paymentAfterInitiate.getCurrency()).isEqualTo(ORDER_CURRENCY);

        var callbackResponse = restTemplate.postForEntity(
                "/api/v1/payments/callback/wompi",
                new HttpEntity<>("""
                        {
                          "data": {
                            "transaction": {
                              "id": "tx-approved-001",
                              "status": "APPROVED",
                              "reference": "%s"
                            }
                          }
                        }
                        """.formatted(reference), headers),
                Void.class
        );

        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APROBADO);
        assertThat(payment.getPaidAt()).isNotNull();

        verify(rabbitTemplate).convertAndSend(
                eq(exchange),
                eq(approvedRoutingKey),
                any(PaymentApprovedMessage.class)
        );

        var getOrderRequest = orderServiceMock.takeRequest();
        assertThat(getOrderRequest.getPath()).contains("/api/v1/orders/");

        var approveRequest = orderServiceMock.takeRequest();
        assertThat(approveRequest.getMethod()).isEqualTo("PATCH");
        assertThat(approveRequest.getPath()).contains("confirm");
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
