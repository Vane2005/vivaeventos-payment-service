package co.edu.univalle.payment.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitiatePaymentRequest(
        @NotNull UUID orderId
) {}