package com.freeswitch.calling.dto;

/** Response body for {@code DELETE /api/v1/voice/users/{extension}}. */
public record DeleteVoiceUserResponse(String extension, String status) {

    public static DeleteVoiceUserResponse deleted(String extension) {
        return new DeleteVoiceUserResponse(extension, "DELETED");
    }
}
