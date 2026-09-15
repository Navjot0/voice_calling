package com.freeswitch.calling.model;

/**
 * Lifecycle states of a voice call.
 *
 * <p>A call does not necessarily pass through every state - a successful
 * flow moves INITIATED -&gt; RINGING -&gt; ANSWERED -&gt; COMPLETED, while an
 * unsuccessful one terminates instead in FAILED, BUSY or NO_ANSWER.
 * Command acceptance (INITIATED) is deliberately distinct from the call
 * actually ringing or being answered, since FreeSWITCH reports those as
 * separate, later events.
 */
public enum CallStatus {
    INITIATED,
    RINGING,
    ANSWERED,
    COMPLETED,
    FAILED,
    BUSY,
    NO_ANSWER
}
