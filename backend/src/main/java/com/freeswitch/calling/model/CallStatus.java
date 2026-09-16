package com.freeswitch.calling.model;

import java.util.Set;

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
    NO_ANSWER;

    private static final Set<String> NO_ANSWER_CAUSES = Set.of(
            "NO_ANSWER", "NO_USER_RESPONSE", "ALLOTTED_TIMEOUT", "ORIGINATOR_CANCEL");

    /**
     * Resolves the terminal status a call ended in, from whether it was ever
     * answered and FreeSWITCH's raw hangup cause. Shared by
     * {@code FreeSwitchEventListener} (resolving it the moment a live call
     * hangs up) and {@code CallResponse.from(CallEntity)} (recomputing the
     * same thing later from an archived {@code cdr} row, which has no status
     * column of its own) - both must agree, so the rule lives in one place.
     */
    public static CallStatus resolveTerminal(boolean wasAnswered, String hangupCause) {
        if (wasAnswered) {
            return COMPLETED;
        }
        if (hangupCause == null) {
            return FAILED;
        }
        if ("USER_BUSY".equals(hangupCause)) {
            return BUSY;
        }
        if (NO_ANSWER_CAUSES.contains(hangupCause)) {
            return NO_ANSWER;
        }
        return FAILED;
    }
}
