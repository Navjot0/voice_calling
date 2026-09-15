package com.freeswitch.calling.exception;

/**
 * Thrown when an extension fails format validation. Bean validation on the
 * request DTO and path variables already rejects malformed input before it
 * reaches {@code FreeSwitchDirectoryService}; this exists as a defense-in-depth
 * check at the point where the extension is turned into a filesystem path, so
 * that path can never be reached with an unvalidated value regardless of caller.
 */
public class InvalidVoiceUserExtensionException extends RuntimeException {

    public InvalidVoiceUserExtensionException(String extension) {
        super("Invalid extension format: " + extension);
    }
}
