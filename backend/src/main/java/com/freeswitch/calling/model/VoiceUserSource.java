package com.freeswitch.calling.model;

/**
 * Distinguishes SIP directory users this application provisioned from ones
 * that already existed on the FreeSWITCH server (e.g. 1001/1002). The
 * FreeSWITCH directory itself is the source of truth for which users exist;
 * this distinction is tracked separately (see {@code VoiceUserRepository})
 * and is best-effort - it resets if the application restarts, since nothing
 * on disk records provenance.
 */
public enum VoiceUserSource {
    PROVISIONED_BY_API,
    EXISTING_EXTERNAL_USER
}
