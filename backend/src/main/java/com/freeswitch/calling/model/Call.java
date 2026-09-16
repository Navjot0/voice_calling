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
    private volatile String hangupCause;
    private volatile String blegUuid;

    public Call(String callId, String from, String to, CallDirection direction, CallStatus initialStatus) {
        this.callId = callId;
        this.from = from;
        this.to = to;
        this.direction = direction;
        this.status = initialStatus;
        this.createdAt = Instant.now();
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

    /**
     * Records the FreeSWITCH-assigned UUID of the bridged destination leg
     * (the {@code to} side), once known - see the {@code CHANNEL_BRIDGE}
     * handling in {@code FreeSwitchEventListener}. No-op once already
     * recorded or once the call has terminated.
     */
    public synchronized void recordBridgeLeg(String blegUuid) {
        if (isTerminal() || this.blegUuid != null) {
            return;
        }
        this.blegUuid = blegUuid;
    }

    /** Moves the call into a terminal status and timestamps completion. Idempotent once terminal. */
    public synchronized void markTerminal(CallStatus terminalStatus) {
        markTerminal(terminalStatus, null);
    }

    /**
     * Moves the call into a terminal status, timestamps completion, and
     * records the raw FreeSWITCH hangup cause (e.g. {@code NORMAL_CLEARING},
     * {@code MEDIA_TIMEOUT}) for archival in the {@code cdr} table - see
     * {@code PersistentCallRepository}. Idempotent once terminal.
     */
    public synchronized void markTerminal(CallStatus terminalStatus, String hangupCause) {
        if (isTerminal()) {
            return;
        }
        this.status = terminalStatus;
        this.hangupCause = hangupCause;
        this.completedAt = Instant.now();
    }

    public synchronized boolean isTerminal() {
        return TERMINAL_STATUSES.contains(status);
    }
}
