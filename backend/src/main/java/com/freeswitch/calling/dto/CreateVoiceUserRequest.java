package com.freeswitch.calling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/voice/users}.
 *
 * <p>{@code extension} is restricted to digits only (2-15 of them) - the
 * same convention used for call extensions elsewhere in this app - which
 * also happens to make path traversal in the generated filename structurally
 * impossible (see {@code FreeSwitchDirectoryService}) rather than merely
 * filtered.
 */
public record CreateVoiceUserRequest(

        @NotBlank(message = "Extension is required")
        @Pattern(regexp = "^[0-9]{2,15}$", message = "Extension must be numeric (2-15 digits)")
        String extension,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must be at most 128 characters")
        String password,

        @NotBlank(message = "Name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        String name
) {
    /**
     * Overridden so the password can never appear if this request is ever
     * logged, printed, or included in a future error message - records
     * generate a toString() including every component by default, which
     * would otherwise leak it.
     */
    @Override
    public String toString() {
        return "CreateVoiceUserRequest[extension=" + extension + ", password=***REDACTED***, name=" + name + "]";
    }
}
