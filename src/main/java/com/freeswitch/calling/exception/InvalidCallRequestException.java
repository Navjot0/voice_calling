package com.freeswitch.calling.exception;

/**
 * Thrown for request-level business rule violations that plain bean
 * validation cannot express, e.g. {@code from} and {@code to} being equal.
 */
public class InvalidCallRequestException extends RuntimeException {

    public InvalidCallRequestException(String message) {
        super(message);
    }
}
