package com.freeswitch.calling.model;

import lombok.Getter;

import java.time.Instant;
import java.util.Set;

/**
 * In-memory representation of a single voice call tracked by the platform.
 *
 * <p>The call's identity ({@link #callId}) is the FreeSWITCH channel UUID
 * for the originating leg, not a locally generated identifier - see
 * {@link com.freeswitch.calling.service.VoiceCallService}.
 *
 * <p>Instances are mutated concurrently: the REST-triggered service thread
 * creates and reads the call, while a FreeSWITCH ESL event-handling thread
 * updates its status as the underlying call progresses. State transitions
 * are therefore synchronized and timestamped internally rather than exposed
 * as plain setters.
 */
@Getter
public class Call {

    private static final Set<CallStatus> TERMINAL_STATUSES =
            Set.of(CallStatus.COMPLETED, CallStatus.FAILED, CallStatus.BUSY, CallStatus.NO_ANSWER);

    private final String callId;
    private final String from;
    private final String to;
    private final CallDirection direction;
    private final Instant createdAt;

    private volatile CallStatus status;
    private volatile Instant answeredAt;
    private volatile Instant completedAt;

    public Call(String callId, String from, String to, CallDirection direction, CallStatus initialStatus) {
        this.callId = callId;
        this.from = from;
        this.to = to;
        this.direction = direction;
        this.status = initialStatus;
        this.createdAt = Instant.now();
    }

    /**
     * Full-state constructor used to reconstruct a {@link Call} from
     * previously persisted data (see {@code PersistentCallRepository}).
     * Unlike the primary constructor, this does not stamp any timestamp
     * itself - the caller supplies the exact previously recorded values, so
     * reloading a call from storage never loses or overwrites its history.
     */
    public Call(String callId, String from, String to, CallDirection direction, CallStatus status,
                Instant createdAt, Instant answeredAt, Instant completedAt) {
        this.callId = callId;
        this.from = from;
        this.to = to;
        this.direction = direction;
        this.status = status;
        this.createdAt = createdAt;
        this.answeredAt = answeredAt;
        this.completedAt = completedAt;
    }

    /** Moves the call into a non-terminal status (e.g. RINGING). No-op if the call has already terminated. */
    public synchronized void updateStatus(CallStatus newStatus) {
        if (isTerminal()) {
            return;
        }
        this.status = newStatus;
    }

    /** Marks the call as answered and timestamps it. No-op if the call has already terminated. */
    public synchronized void markAnswered() {
        if (isTerminal()) {
            return;
        }
        this.status = CallStatus.ANSWERED;
        this.answeredAt = Instant.now();
    }

    /** Moves the call into a terminal status and timestamps completion. Idempotent once terminal. */
    public synchronized void markTerminal(CallStatus terminalStatus) {
        if (isTerminal()) {
            return;
        }
        this.status = terminalStatus;
        this.completedAt = Instant.now();
    }

    public synchronized boolean isTerminal() {
        return TERMINAL_STATUSES.contains(status);
    }
}
