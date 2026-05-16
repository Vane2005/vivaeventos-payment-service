package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.exception.DuplicatePaymentException;
import co.edu.univalle.payment.domain.exception.InvalidOrderStateException;
import co.edu.univalle.payment.domain.model.Order;
import co.edu.univalle.payment.domain.model.OrderStatus;
import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.OrderServicePort;
import co.edu.univalle.payment.domain.port.PaymentGatewayPort;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitiatePaymentUseCaseTest {

    @Mock
    PaymentRepositoryPort paymentRepository;

    @Mock
    OrderServicePort orderService;

    @Mock
    PaymentGatewayPort paymentGateway;

    @InjectMocks
    InitiatePaymentUseCase useCase;

    @Test
    void rejectsOrderNotPendingPayment() {
        var orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenReturn(
                new Order(orderId, OrderStatus.PAID, BigDecimal.TEN, "COP", "a@b.com")
        );

        assertThatThrownBy(() -> useCase.execute(orderId))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void rejectsDuplicateApprovedPayment() {
        var orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenReturn(
                new Order(orderId, OrderStatus.PENDING, BigDecimal.TEN, "COP", "a@b.com")
        );
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(orderId), any(PaymentStatus[].class))).thenReturn(true);
        when(paymentRepository.findActiveByOrderId(orderId)).thenReturn(Optional.of(
                new Payment(
                        UUID.randomUUID(), orderId, BigDecimal.TEN, "COP", PaymentStatus.APROBADO,
                        "ref", "tx", "url", null, "a@b.com",
                        Instant.now(), Instant.now(), Instant.now()
                )
        ));

        assertThatThrownBy(() -> useCase.execute(orderId))
                .isInstanceOf(DuplicatePaymentException.class);
    }

    @Test
    void initiatesPaymentSuccessfully() {
        var orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenReturn(
                new Order(orderId, OrderStatus.PENDING, BigDecimal.valueOf(100), "COP", "a@b.com")
        );
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(orderId), any(PaymentStatus[].class))).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.createCheckout(any())).thenReturn(
                new PaymentGatewayPort.GatewayCheckoutResult("tx-1", "https://checkout.test", "PENDING")
        );

        var response = useCase.execute(orderId);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.test");
        verify(paymentGateway).createCheckout(any());
        verify(paymentRepository, times(2)).save(any());
    }
}
