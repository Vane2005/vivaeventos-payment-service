package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.OrderServicePort;
import co.edu.univalle.payment.domain.port.PaymentEventPublisherPort;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovePaymentUseCaseTest {

    @Mock
    private PaymentRepositoryPort repository;

    @Mock
    private OrderServicePort orderService;

    @Mock
    private PaymentEventPublisherPort publisher;

    @InjectMocks
    private ApprovePaymentUseCase useCase;

    @Test
    void shouldDoNothingIfAlreadyApproved() {

        Payment payment = mock(Payment.class);

        when(payment.status())
                .thenReturn(PaymentStatus.APROBADO);

        useCase.execute(payment);

        verifyNoInteractions(repository);
        verifyNoInteractions(orderService);
        verifyNoInteractions(publisher);
    }
}