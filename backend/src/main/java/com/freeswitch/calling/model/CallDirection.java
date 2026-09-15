package com.freeswitch.calling.model;

/**
 * Direction of a voice call relative to the platform.
 *
 * <p>Only OUTBOUND is produced by this step of the implementation; INBOUND
 * is modeled now so the state machine and storage layer do not need to
 * change when inbound call handling is added later.
 */
public enum CallDirection {
    INBOUND,
    OUTBOUND
}
