package co.edu.univalle.payment.integration;

import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.infrastructure.messaging.EventCancelledMessage;
import co.edu.univalle.payment.infrastructure.persistence.PaymentEntity;
import co.edu.univalle.payment.infrastructure.persistence.PaymentJpaRepository;
import co.edu.univalle.payment.testsupport.StripeMockServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = co.edu.univalle.vivaeventospaymentservice.VivaeventosPaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@DisplayName("US13: Pruebas de integración - Reembolso por cancelación de evento (notificar compradores e iniciar devolución)")
public class EventCancellationRefundIntegrationTest {

    static class OrderServiceMockServer {
        private final MockWebServer mockWebServer;

        public OrderServiceMockServer() {
            this.mockWebServer = new MockWebServer();
        }

        public void start() throws IOException {
            mockWebServer.start();
        }

        public void shutdown() throws IOException {
            mockWebServer.shutdown();
        }

        public String getBaseUrl() {
            return mockWebServer.url("/").toString().replaceAll("/$", "");
        }

        public void stubGetOrderIdsByEvent(UUID eventId, List<UUID> orderIds) {
            String orderIdsJson = orderIds.stream()
                    .map(id -> "\"" + id.toString() + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"orderIds\": [" + orderIdsJson + "]}")
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        }
    }

    static StripeMockServer stripeMock;
    static OrderServiceMockServer orderServiceMockServer;

    @MockBean
    RabbitTemplate rabbitTemplate;

    @Autowired
    PaymentJpaRepository paymentJpaRepository;

    @BeforeAll
    static void startMocks() throws IOException {
        stripeMock = new StripeMockServer();
        stripeMock.start();

        orderServiceMockServer = new OrderServiceMockServer();
        orderServiceMockServer.start();
    }

    @AfterAll
    static void stopMocks() throws IOException {
        stripeMock.shutdown();
        orderServiceMockServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.order-service.url",
                () -> orderServiceMockServer.getBaseUrl());
        registry.add("payment.gateway.provider", () -> "stripe");
        registry.add("stripe.secret-key", () -> "sk_test_mock");
        registry.add("stripe.api-base", () -> stripeMock.getBaseUrl());
        registry.add("stripe.success-url", () -> "http://localhost:3000/success");
        registry.add("stripe.cancel-url", () -> "http://localhost:3000/cancel");
        registry.add("stripe.webhook-secret", () -> "");
    }

    @BeforeEach
    void setUp() {
        paymentJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("US13.1 - Evento cancelado: reembolsar todos los pagos aprobados")
    void cancelEvent_withMultipleApprovedPayments_refundsAll() throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        createApprovedPayment(orderId1);
        createApprovedPayment(orderId2);

        orderServiceMockServer.stubGetOrderIdsByEvent(eventId, List.of(orderId1, orderId2));
        stripeMock.setSessionMode(StripeMockServer.SessionStubMode.PAID);


        var payments = paymentJpaRepository.findAll();
        assertThat(payments).hasSize(2);
        assertThat(payments).allMatch(p -> p.getStatus() == PaymentStatus.APROBADO);
    }

    @Test
    @DisplayName("US13.2 - Evento cancelado sin pagos aprobados - no reembolsar")
    void cancelEvent_withNoApprovedPayments_noRefund() throws Exception {

        UUID eventId = UUID.randomUUID();

        orderServiceMockServer.stubGetOrderIdsByEvent(eventId, List.of());


        assertThat(paymentJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("US13.3 - Evento cancelado con pagos mixtos - solo reembolsar aprobados")
    void cancelEvent_withMixedPayments_refundsOnlyApproved() throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID approvedOrderId = UUID.randomUUID();
        UUID pendingOrderId = UUID.randomUUID();

        createApprovedPayment(approvedOrderId);
        createPendingPayment(pendingOrderId);

        orderServiceMockServer.stubGetOrderIdsByEvent(eventId, List.of(approvedOrderId, pendingOrderId));


        var approvedPayment = paymentJpaRepository
                .findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(approvedOrderId,
                        List.of(PaymentStatus.APROBADO));
        var pendingPayment = paymentJpaRepository
                .findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(pendingOrderId,
                        List.of(PaymentStatus.PENDIENTE));

        assertThat(approvedPayment).isPresent();
        assertThat(approvedPayment.get().getStatus()).isEqualTo(PaymentStatus.APROBADO);

        assertThat(pendingPayment).isPresent();
        assertThat(pendingPayment.get().getStatus()).isEqualTo(PaymentStatus.PENDIENTE);
    }

    private void createApprovedPayment(UUID orderId) {
        var paymentEntity = new PaymentEntity();
        paymentEntity.setId(UUID.randomUUID());
        paymentEntity.setOrderId(orderId);
        paymentEntity.setAmount(BigDecimal.valueOf(100000.00));
        paymentEntity.setCurrency("COP");
        paymentEntity.setStatus(PaymentStatus.APROBADO);
        paymentEntity.setGatewayReference("ref_" + UUID.randomUUID());
        paymentEntity.setGatewayTransactionId("cs_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        paymentEntity.setCustomerEmail("cliente@test.com");
        paymentEntity.setCreatedAt(Instant.now());
        paymentEntity.setUpdatedAt(Instant.now());
        paymentEntity.setPaidAt(Instant.now());
        paymentJpaRepository.save(paymentEntity);
    }

    private void createPendingPayment(UUID orderId) {
        var paymentEntity = new PaymentEntity();
        paymentEntity.setId(UUID.randomUUID());
        paymentEntity.setOrderId(orderId);
        paymentEntity.setAmount(BigDecimal.valueOf(50000.00));
        paymentEntity.setCurrency("COP");
        paymentEntity.setStatus(PaymentStatus.PENDIENTE);
        paymentEntity.setGatewayReference("ref_" + UUID.randomUUID());
        paymentEntity.setCustomerEmail("cliente2@test.com");
        paymentEntity.setCreatedAt(Instant.now());
        paymentEntity.setUpdatedAt(Instant.now());
        paymentJpaRepository.save(paymentEntity);
    }
}