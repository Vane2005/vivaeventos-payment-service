package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.exception.DuplicatePaymentException;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreventDuplicateChargeUseCaseTest {

    @Mock
    private PaymentRepositoryPort repository;

    @InjectMocks
    private PreventDuplicateChargeUseCase useCase;

    @Test
    void shouldAllowPaymentWhenNoActivePaymentExists() {

        UUID orderId = UUID.randomUUID();

        when(repository.existsByOrderIdAndStatusIn(
                eq(orderId),
                eq(co.edu.univalle.payment.domain.model.PaymentStatus.PENDIENTE),
                eq(co.edu.univalle.payment.domain.model.PaymentStatus.EN_PROCESO),
                eq(co.edu.univalle.payment.domain.model.PaymentStatus.APROBADO)
        )).thenReturn(false);

        useCase.validate(orderId);

        verify(repository).existsByOrderIdAndStatusIn(
                eq(orderId),
                eq(co.edu.univalle.payment.domain.model.PaymentStatus.PENDIENTE),
                eq(co.edu.univalle.payment.domain.model.PaymentStatus.EN_PROCESO),
                eq(co.edu.univalle.payment.domain.model.PaymentStatus.APROBADO)
        );
    }

    @Test
    void shouldThrowDuplicatePaymentException() {

        UUID orderId = UUID.randomUUID();

        when(repository.existsByOrderIdAndStatusIn(
                eq(orderId),
                eq(co.edu.univalle.payment.domain.model.PaymentStatus.PENDIENTE),
                eq(co.edu.univalle.payment.domain.model.PaymentStatus.EN_PROCESO),
                eq(co.edu.univalle.payment.domain.model.PaymentStatus.APROBADO)
        )).thenReturn(true);

        assertThrows(
                DuplicatePaymentException.class,
                () -> useCase.validate(orderId)
        );
    }
}