package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.exception.DuplicatePaymentException;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreventDuplicateChargeUseCaseTest {

    @Mock
    private PaymentRepositoryPort repository;

    @InjectMocks
    private PreventDuplicateChargeUseCase useCase;

    @Test
    void shouldAllowPaymentWhenNoActivePaymentExists() {

        UUID orderId = UUID.randomUUID();

        when(repository.existsByOrderIdAndStatusIn(any(), any()))
                .thenReturn(false);

        useCase.validate(orderId);

        verify(repository).existsByOrderIdAndStatusIn(any(), any());
    }

    @Test
    void shouldThrowDuplicatePaymentException() {

        UUID orderId = UUID.randomUUID();

        when(repository.existsByOrderIdAndStatusIn(any(), any()))
                .thenReturn(true);

        assertThrows(
                DuplicatePaymentException.class,
                () -> useCase.validate(orderId)
        );
    }
}