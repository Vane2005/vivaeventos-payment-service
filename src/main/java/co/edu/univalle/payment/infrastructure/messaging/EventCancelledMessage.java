package co.edu.univalle.payment.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

public class EventCancelledMessage {
    private UUID eventId;
    private String eventName;
    private String cancelledBy;
    private String reason;
    private Instant cancelledAt;

    public EventCancelledMessage() {}

    public EventCancelledMessage(UUID eventId, String eventName, String cancelledBy, String reason, Instant cancelledAt) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.cancelledBy = cancelledBy;
        this.reason = reason;
        this.cancelledAt = cancelledAt;
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}