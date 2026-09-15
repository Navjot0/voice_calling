package com.freeswitch.calling.dto;

import com.freeswitch.calling.model.CallDirection;
import com.freeswitch.calling.model.CallStatus;

/**
 * Response body for {@code POST /api/v1/voice/calls}.
 *
 * <p>{@code status} reflects only that FreeSWITCH accepted the originate
 * command (INITIATED) - it is not confirmation that anyone answered.
 */
public record CreateCallResponse(
        String callId,
        CallStatus status,
        String from,
        String to,
        CallDirection direction
) {
}
