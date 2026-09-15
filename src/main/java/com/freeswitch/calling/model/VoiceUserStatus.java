package com.freeswitch.calling.model;

/**
 * State of a SIP directory user as seen by the provisioning API.
 *
 * <p>Only ACTIVE is produced today - a user either has a directory entry
 * (ACTIVE) or it doesn't exist ({@code 404}). This is intentionally not
 * reused for the delete operation's result (see
 * {@code dto.DeleteVoiceUserResponse}), which reports a one-off "DELETED"
 * outcome rather than an ongoing state.
 */
public enum VoiceUserStatus {
    ACTIVE
}
