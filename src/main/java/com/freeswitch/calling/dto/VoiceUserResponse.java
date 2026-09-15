package com.freeswitch.calling.dto;

import com.freeswitch.calling.model.VoiceUser;
import com.freeswitch.calling.model.VoiceUserSource;
import com.freeswitch.calling.model.VoiceUserStatus;

/**
 * Response body for the create/get/list voice-user endpoints. Never carries
 * a password field - there is nothing to omit by accident, since
 * {@link VoiceUser} itself never holds one.
 */
public record VoiceUserResponse(
        String extension,
        String name,
        VoiceUserStatus status,
        VoiceUserSource source
) {
    public static VoiceUserResponse from(VoiceUser user) {
        return new VoiceUserResponse(user.extension(), user.name(), user.status(), user.source());
    }
}
