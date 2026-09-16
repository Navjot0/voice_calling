package com.freeswitch.calling.dto;

import com.freeswitch.calling.entity.CallEntity;
import com.freeswitch.calling.model.Call;
import com.freeswitch.calling.model.CallDirection;
import com.freeswitch.calling.model.CallStatus;

import java.time.Instant;

/**
 * Response body for {@code GET /api/v1/voice/calls/{callId}} (a single,
 * live-tracked call) and {@code GET /api/v1/voice/calls} (call history, one
 * entry per archived {@code cdr} row) - the full lifecycle snapshot of a
 * call either way.
 */
public record CallResponse(
        String callId,
        CallStatus status,
        String from,
        String to,
        CallDirection direction,
        Instant createdAt,
        Instant answeredAt,
        Instant completedAt
) {
    public static CallResponse from(Call call) {
        return new CallResponse(
                call.getCallId(),
                call.getStatus(),
                call.getFrom(),
                call.getTo(),
                call.getDirection(),
                call.getCreatedAt(),
                call.getAnsweredAt(),
                call.getCompletedAt()
        );
    }

    /**
     * Reconstructs a finished call's snapshot from its archived {@code cdr}
     * row. {@code cdr} has no direction/status column: direction is always
     * OUTBOUND (this API never originates anything else), and status is
     * recomputed via {@link CallStatus#resolveTerminal} from whether the row
     * has an answer_stamp and its hangup_cause - exactly reproducing the
     * determination {@code FreeSwitchEventListener} made when the call
     * actually ended.
     */
    public static CallResponse from(CallEntity entity) {
        CallStatus status = CallStatus.resolveTerminal(entity.getAnswerStamp() != null, entity.getHangupCause());
        return new CallResponse(
                entity.getUuid(),
                status,
                entity.getCallerIdNumber(),
                entity.getDestinationNumber(),
                CallDirection.OUTBOUND,
                entity.getStartStamp(),
                entity.getAnswerStamp(),
                entity.getEndStamp()
        );
    }
}
