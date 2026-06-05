package co.edu.univalle.payment.application.usecase;

import co.edu.univalle.payment.domain.exception.PaymentNotFoundException;
import co.edu.univalle.payment.domain.model.Payment;
import co.edu.univalle.payment.domain.model.PaymentStatus;
import co.edu.univalle.payment.domain.port.PaymentRepositoryPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPaymentUseCaseTest {

    @Mock
    private PaymentRepositoryPort repository;

    @InjectMocks
    private GetPaymentUseCase useCase;

    @Test
    void shouldReturnPayment() {

        UUID id = UUID.randomUUID();

        Payment payment = new Payment(
                id,
                UUID.randomUUID(),
                BigDecimal.TEN,
                "COP",
                PaymentStatus.APROBADO,
                "ref",
                "tx",
                null,
                null,
                "test@test.com",
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(repository.findById(id))
                .thenReturn(Optional.of(payment));

        var response = useCase.execute(id);

        assertEquals(id, response.paymentId());
        assertEquals("COP", response.currency());
        assertEquals(PaymentStatus.APROBADO, response.status());
    }

    @Test
    void shouldThrowWhenPaymentDoesNotExist() {

        UUID id = UUID.randomUUID();

        when(repository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> useCase.execute(id)
        );
    }
}