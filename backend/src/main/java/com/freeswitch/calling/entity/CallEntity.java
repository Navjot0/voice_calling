package com.freeswitch.calling.entity;

import com.freeswitch.calling.model.CallDirection;
import com.freeswitch.calling.model.CallStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA persistence mapping for a tracked call, stored in the {@code calls}
 * table.
 *
 * <p>Kept separate from {@link com.freeswitch.calling.model.Call} - the
 * plain, synchronized domain object the rest of the application works with -
 * so that persistence concerns (column names, JPA annotations, the no-arg
 * constructor JPA requires) never leak into the domain model or its
 * existing tests. {@code PersistentCallRepository} converts between the two
 * on every read/write.
 */
@Entity
@Table(name = "calls")
public class CallEntity {

    @Id
    @Column(name = "call_id", nullable = false, updatable = false, length = 64)
    private String callId;

    // Mapped to non-reserved column names - "from" and "to" are reserved
    // words in SQL (PostgreSQL rejects an unquoted "from" column outright).
    @Column(name = "from_extension", nullable = false, length = 32)
    private String fromExtension;

    @Column(name = "to_extension", nullable = false, length = 32)
    private String toExtension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CallDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CallStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Required by JPA; not for application use. */
    protected CallEntity() {
    }

    public CallEntity(String callId, String fromExtension, String toExtension, CallDirection direction,
                       CallStatus status, Instant createdAt, Instant answeredAt, Instant completedAt) {
        this.callId = callId;
        this.fromExtension = fromExtension;
        this.toExtension = toExtension;
        this.direction = direction;
        this.status = status;
        this.createdAt = createdAt;
        this.answeredAt = answeredAt;
        this.completedAt = completedAt;
    }

    public String getCallId() {
        return callId;
    }

    public String getFromExtension() {
        return fromExtension;
    }

    public String getToExtension() {
        return toExtension;
    }

    public CallDirection getDirection() {
        return direction;
    }

    public CallStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
