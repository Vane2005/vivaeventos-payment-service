package co.edu.univalle.payment.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus {
    PENDIENTE,
    EN_PROCESO,
    APROBADO,
    FALLIDO;

    @JsonValue
    public String toJson() {
        return this.name(); // → "APROBADO", no 2
    }

    @JsonCreator
    public static PaymentStatus fromJson(String value) {
        return PaymentStatus.valueOf(value);
    }
    }