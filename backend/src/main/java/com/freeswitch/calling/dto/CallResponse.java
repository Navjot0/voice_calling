package com.freeswitch.calling.dto;

import com.freeswitch.calling.model.Call;
import com.freeswitch.calling.model.CallDirection;
import com.freeswitch.calling.model.CallStatus;

import java.time.Instant;

/**
 * Response body for {@code GET /api/v1/voice/calls/{callId}} - the full
 * lifecycle snapshot of a tracked call.
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
}
