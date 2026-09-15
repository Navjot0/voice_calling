package com.freeswitch.calling.exception;

/**
 * Thrown when no usable ESL connection to FreeSWITCH can be established.
 * Kept distinct from {@link FreeSwitchOperationException} so the API layer
 * can report connectivity problems (503) differently from a rejected
 * command (502).
 */
public class FreeSwitchConnectionException extends RuntimeException {

    public FreeSwitchConnectionException(String message) {
        super(message);
    }

    public FreeSwitchConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
