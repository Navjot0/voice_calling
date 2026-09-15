package com.freeswitch.calling.model;

/**
 * Domain representation of a FreeSWITCH SIP directory user.
 *
 * <p>Deliberately holds no password: the SIP password lives only in
 * FreeSWITCH's own directory XML, never in this application's memory beyond
 * the single request that writes it to disk. See
 * {@code FreeSwitchDirectoryService#createUser} for the one place it is used.
 */
public record VoiceUser(String extension, String name, VoiceUserStatus status, VoiceUserSource source) {
}
