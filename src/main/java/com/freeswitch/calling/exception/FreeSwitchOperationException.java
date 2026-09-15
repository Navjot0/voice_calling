package com.freeswitch.calling.exception;

/**
 * Thrown when an ESL connection exists but a specific command (e.g.
 * {@code originate}) failed, timed out, or could not be confirmed sent.
 */
public class FreeSwitchOperationException extends RuntimeException {

    public FreeSwitchOperationException(String message) {
        super(message);
    }

    public FreeSwitchOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
