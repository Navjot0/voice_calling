package com.freeswitch.calling.dto;

import com.freeswitch.calling.entity.CallEntity;
import com.freeswitch.calling.model.Call;
import com.freeswitch.calling.model.CallDirection;
import com.freeswitch.calling.model.CallStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Response body for {@code GET /api/v1/voice/calls/{callId}} (a single,
 * live-tracked call) and {@code GET /api/v1/voice/calls} (call history, one
 * entry per archived {@code cdr} row) - the full lifecycle snapshot of a
 * call either way.
 *
 * <p>{@code duration} (total elapsed seconds, start to end) and
 * {@code billsec} (billable seconds, answer to end - {@code 0} if never
 * answered) are only meaningful once a call has ended, so both are
 * {@code null} while a call is still in progress.
 */
public record CallResponse(
        String callId,
        CallStatus status,
        String from,
        String to,
        CallDirection direction,
        Instant createdAt,
        Instant answeredAt,
        Instant completedAt,
        Integer duration,
        Integer billsec
) {
    public static CallResponse from(Call call) {
        Instant start = call.getCreatedAt();
        Instant answered = call.getAnsweredAt();
        Instant end = call.getCompletedAt();

        // Mirrors the computation PersistentCallRepository.record() does when
        // archiving to the cdr table, so a call reports the same duration/
        // billsec here as it will once it's written to cdr.
        Integer duration = (start != null && end != null)
                ? (int) Duration.between(start, end).getSeconds()
                : null;
        Integer billsec = (end != null)
                ? (answered != null ? (int) Duration.between(answered, end).getSeconds() : 0)
                : null;

        return new CallResponse(
                call.getCallId(),
                call.getStatus(),
                call.getFrom(),
                call.getTo(),
                call.getDirection(),
                start,
                answered,
                end,
                duration,
                billsec
        );
    }

    /**
     * Reconstructs a finished call's snapshot from its archived {@code cdr}
     * row. {@code cdr} has no direction/status column: direction is always
     * OUTBOUND (this API never originates anything else), and status is
     * recomputed via {@link CallStatus#resolveTerminal} from whether the row
     * has an answer_stamp and its hangup_cause - exactly reproducing the
     * determination {@code FreeSwitchEventListener} made when the call
     * actually ended. {@code duration}/{@code billsec} are read straight off
     * the row, since {@code PersistentCallRepository} already computed them
     * at archival time.
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
                entity.getEndStamp(),
                entity.getDuration(),
                entity.getBillsec()
        );
    }
}
