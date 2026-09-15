package com.freeswitch.calling.exception;

import java.time.Instant;

/** Consistent error body returned by every failed API call. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
