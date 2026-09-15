package com.freeswitch.calling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/voice/calls}.
 *
 * <p>Both extensions must be present and look like a SIP extension number.
 * Whether {@code from} may equal {@code to} is a business rule (not a pure
 * format concern) and is enforced in the service layer.
 */
public record CreateCallRequest(

        @NotBlank(message = "Source extension 'from' is required")
        @Pattern(regexp = "^[0-9]{2,15}$", message = "Source extension 'from' must be a numeric extension (2-15 digits)")
        String from,

        @NotBlank(message = "Destination extension 'to' is required")
        @Pattern(regexp = "^[0-9]{2,15}$", message = "Destination extension 'to' must be a numeric extension (2-15 digits)")
        String to
) {
}
