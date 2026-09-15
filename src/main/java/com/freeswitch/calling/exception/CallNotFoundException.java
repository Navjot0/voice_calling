package com.freeswitch.calling.exception;

/** Thrown when a lookup for a call ID finds nothing in the {@code CallRepository}. */
public class CallNotFoundException extends RuntimeException {

    public CallNotFoundException(String callId) {
        super("Call not found: " + callId);
    }
}
