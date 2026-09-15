package com.freeswitch.calling.exception;

/** Thrown when creating an extension that already has a directory entry. */
public class VoiceUserAlreadyExistsException extends RuntimeException {

    public VoiceUserAlreadyExistsException(String extension) {
        super("Extension " + extension + " already exists");
    }
}
