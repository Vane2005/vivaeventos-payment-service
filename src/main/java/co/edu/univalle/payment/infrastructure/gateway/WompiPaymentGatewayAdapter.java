package co.edu.univalle.payment.infrastructure.gateway;

import co.edu.univalle.payment.domain.exception.PaymentDomainException;
import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.port.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.RoundingMode;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "wompi")
@EnableConfigurationProperties(WompiProperties.class)
public class WompiPaymentGatewayAdapter implements PaymentGatewayPort {

    private final WompiProperties properties;
    private final RestClient restClient;

    public WompiPaymentGatewayAdapter(WompiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.privateKey())
                .build();
    }

    @Override
    public GatewayCheckoutResult createCheckout(Payment payment) {
        if (properties.privateKey() == null || properties.privateKey().isBlank()) {
            throw new PaymentDomainException(
                    "WOMPI_PRIVATE_KEY no configurada. Configure las credenciales sandbox de Wompi."
            );
        }

        var amountInCents = payment.amount()
                .multiply(java.math.BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        var body = Map.of(
                "amount_in_cents", amountInCents,
                "currency", payment.currency(),
                "customer_email", payment.customerEmail() != null ? payment.customerEmail() : "cliente@vivaeventos.com",
                "reference", payment.gatewayReference(),
                "redirect_url", properties.redirectUrl(),
                "payment_method", Map.of(
                        "type", "CARD",
                        "installments", 1
                )
        );

        var response = restClient.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(WompiTransactionResponse.class);

        if (response == null || response.data() == null) {
            throw new PaymentDomainException("Respuesta inválida de la pasarela Wompi");
        }

        var data = response.data();
        var checkoutUrl = data.payment_link_url() != null
                ? data.payment_link_url()
                : "https://checkout.wompi.co/l/" + data.id();

        return new GatewayCheckoutResult(
                data.id(),
                checkoutUrl,
                data.status() != null ? data.status() : "PENDING",
                data.error_message()
        );
    }

    @Override
    public GatewayTransactionStatus queryTransactionStatus(String gatewayTransactionId) {
        var response = restClient.get()
                .uri("/transactions/{id}", gatewayTransactionId)
                .retrieve()
                .body(WompiTransactionResponse.class);

        if (response == null || response.data() == null) {
            throw new PaymentDomainException("No se pudo consultar la transacción: " + gatewayTransactionId);
        }

        var data = response.data();
        return new GatewayTransactionStatus(
                data.id(),
                data.status(),
                data.status_message()
        );
    }

    record WompiTransactionResponse(WompiTransactionData data) {}

    record WompiTransactionData(
            String id,
            String status,
            String status_message,
            String payment_link_url,
            String error_message
    ) {}
}
