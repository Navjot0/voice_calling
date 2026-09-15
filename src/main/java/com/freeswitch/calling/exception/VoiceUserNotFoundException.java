package com.freeswitch.calling.exception;

/** Thrown when an extension has no corresponding directory entry. */
public class VoiceUserNotFoundException extends RuntimeException {

    public VoiceUserNotFoundException(String extension) {
        super("Extension " + extension + " not found");
    }
}
