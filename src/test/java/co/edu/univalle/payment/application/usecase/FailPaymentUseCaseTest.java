package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.PaymentEventPublisherPort;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FailPaymentUseCaseTest {

    @Mock
    private PaymentRepositoryPort repository;

    @Mock
    private PaymentEventPublisherPort publisher;

    @InjectMocks
    private FailPaymentUseCase useCase;

    @Test
    void shouldDoNothingIfAlreadyFailed() {

        Payment payment = mock(Payment.class);

        when(payment.status())
                .thenReturn(PaymentStatus.FALLIDO);

        useCase.execute(payment, "error");

        verifyNoInteractions(repository);
        verifyNoInteractions(publisher);
    }
}