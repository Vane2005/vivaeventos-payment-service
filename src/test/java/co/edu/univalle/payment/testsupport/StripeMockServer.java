package co.edu.univalle.payment.testsupport;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simula respuestas mínimas de la API REST de Stripe para tests de integración.
 */
public class StripeMockServer {

    private final MockWebServer mockWebServer = new MockWebServer();
    private final AtomicReference<SessionStubMode> sessionMode = new AtomicReference<>(SessionStubMode.PENDING);

    private String lastSessionId;
    private String lastPaymentIntentId;

    public enum SessionStubMode {
        PENDING, PAID, EXPIRED, DECLINED_IMMEDIATE, AUTH_ERROR
    }

    public void start() throws IOException {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                var path = request.getRequestUrl() != null ? request.getRequestUrl().encodedPath() : "";
                if ("POST".equals(request.getMethod()) && path.endsWith("/v1/checkout/sessions")) {
                    if (sessionMode.get() == SessionStubMode.AUTH_ERROR) {
                        return new MockResponse()
                                .setResponseCode(401)
                                .setBody("""
                                        {"error":{"type":"invalid_request_error","message":"Invalid API Key"}}
                                        """)
                                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    }
                    return createSessionResponse();
                }
                if ("GET".equals(request.getMethod()) && path.contains("/v1/checkout/sessions/")) {
                    return getSessionResponse(path);
                }
                if ("POST".equals(request.getMethod()) && path.endsWith("/v1/refunds")) {
                    return refundResponse();
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        mockWebServer.start();
    }

    public void shutdown() throws IOException {
        mockWebServer.shutdown();
    }

    public String getBaseUrl() {
        return mockWebServer.url("/").toString().replaceAll("/$", "");
    }

    public String getLastSessionId() {
        return lastSessionId;
    }

    public void setSessionMode(SessionStubMode mode) {
        sessionMode.set(mode);
    }

    public void stubCreateSessionPending() {
        sessionMode.set(SessionStubMode.PENDING);
    }

    public void stubCreateSessionPaid() {
        sessionMode.set(SessionStubMode.PAID);
    }

    public void stubSessionExpired() {
        sessionMode.set(SessionStubMode.EXPIRED);
    }

    public void stubCreateSessionDeclined() {
        sessionMode.set(SessionStubMode.DECLINED_IMMEDIATE);
    }

    public void stubCreateSessionError() {
        sessionMode.set(SessionStubMode.DECLINED_IMMEDIATE);
    }

    public void stubCreateSessionUnauthorized() {
        sessionMode.set(SessionStubMode.AUTH_ERROR);
    }

    private MockResponse createSessionResponse() {
        lastSessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        lastPaymentIntentId = "pi_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        var mode = sessionMode.get();
        var status = switch (mode) {
            case PAID -> "complete";
            case EXPIRED, DECLINED_IMMEDIATE -> "expired";
            default -> "open";
        };
        var paymentStatus = mode == SessionStubMode.PAID ? "paid" : "unpaid";

        var body = """
                {
                  "id": "%s",
                  "object": "checkout.session",
                  "url": "https://checkout.stripe.test/%s",
                  "status": "%s",
                  "payment_status": "%s",
                  "payment_intent": "%s"
                }
                """.formatted(lastSessionId, lastSessionId, status, paymentStatus, lastPaymentIntentId);

        return jsonOk(body);
    }

    private MockResponse getSessionResponse(String path) {
        var sessionId = path.substring(path.lastIndexOf('/') + 1);
        var mode = sessionMode.get();

        var status = switch (mode) {
            case PAID -> "complete";
            case EXPIRED, DECLINED_IMMEDIATE -> "expired";
            case PENDING, AUTH_ERROR -> "open";
        };
        var paymentStatus = mode == SessionStubMode.PAID ? "paid" : "unpaid";

        var body = """
                {
                  "id": "%s",
                  "object": "checkout.session",
                  "status": "%s",
                  "payment_status": "%s",
                  "payment_intent": "%s"
                }
                """.formatted(sessionId, status, paymentStatus, lastPaymentIntentId);

        return jsonOk(body);
    }

    private MockResponse refundResponse() {
        var body = """
                {
                  "id": "re_test_%s",
                  "object": "refund",
                  "status": "succeeded"
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8));

        return jsonOk(body);
    }

    private static MockResponse jsonOk(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    public static String checkoutSessionCompletedWebhook(String clientReferenceId, String sessionId) {
        return webhookEvent(
                "checkout.session.completed",
                sessionPayload(sessionId, clientReferenceId, "complete", "paid")
        );
    }

    public static String checkoutSessionExpiredWebhook(String clientReferenceId, String sessionId) {
        return webhookEvent(
                "checkout.session.expired",
                sessionPayload(sessionId, clientReferenceId, "expired", "unpaid")
        );
    }

    public void reset() {
        sessionMode.set(SessionStubMode.PENDING);
    }

    private static String webhookEvent(String type, String sessionObjectJson) {
        return """
                {
                  "id": "evt_test_%s",
                  "object": "event",
                  "api_version": "2024-11-20.acacia",
                  "created": 1710000000,
                  "type": "%s",
                  "livemode": false,
                  "data": {
                    "object": %s
                  }
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8), type, sessionObjectJson);
    }

    private static String sessionPayload(String sessionId, String clientReferenceId, String status, String paymentStatus) {
        return """
                {
                  "id": "%s",
                  "object": "checkout.session",
                  "client_reference_id": "%s",
                  "status": "%s",
                  "payment_status": "%s"
                }
                """.formatted(sessionId, clientReferenceId, status, paymentStatus);
    }
}
