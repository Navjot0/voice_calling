package com.freeswitch.calling.exception;

/**
 * Thrown when an operation targets an extension configured as protected
 * (see {@code freeswitch.directory.protected-extensions}, defaults to
 * 1001/1002) - at minimum, deletion of these must never succeed through
 * the API.
 */
public class ProtectedVoiceUserException extends RuntimeException {

    public ProtectedVoiceUserException(String extension) {
        super("Extension " + extension + " is protected and cannot be deleted");
    }
}
