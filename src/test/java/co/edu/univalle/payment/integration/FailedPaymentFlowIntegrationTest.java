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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = co.edu.univalle.vivaeventospaymentservice.VivaeventosPaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class FailedPaymentFlowIntegrationTest {

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

    @Test
    void failedPayment_keepsOrderPending_doesNotPublishApprovedEvent() throws Exception {
        var orderId = UUID.randomUUID();

        orderServiceMock.enqueue(new MockResponse()
                .setBody("""
                        {
                          "id": "%s",
                          "status": "PENDING",
                          "totalPrice": 150000.00,
                          "customerEmail": "correo.real@ejemplo.com",
                          "currency": "COP"
                        }
                        """.formatted(orderId))
                .addHeader("Content-Type", "application/json"));

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
        assertThat(initiateResponse.getBody()).isNotNull();

        var reference = initiateResponse.getBody().gatewayReference();

        var callbackResponse = restTemplate.postForEntity(
                "/api/v1/payments/callback/wompi",
                new HttpEntity<>("""
                        {
                          "data": {
                            "transaction": {
                              "id": "tx-declined-001",
                              "status": "DECLINED",
                              "reference": "%s"
                            }
                          }
                        }
                        """.formatted(reference), headers),
                Void.class
        );

        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var payment = paymentJpaRepository.findByGatewayReference(reference).orElseThrow();
        assertThat(payment.getCustomerEmail()).isEqualTo("correo.real@ejemplo.com");
        assertThat(payment.getCurrency()).isEqualTo("COP");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FALLIDO);
        assertThat(payment.getFailureReason()).isNotBlank();

        verify(rabbitTemplate, never()).convertAndSend(
                eq(exchange),
                eq(approvedRoutingKey),
                any(PaymentApprovedMessage.class)
        );
        assertThat(orderServiceMock.getRequestCount()).isEqualTo(1);
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
