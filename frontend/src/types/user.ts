/**
 * Mirrors `com.freeswitch.calling.model.VoiceUserStatus`. Only ACTIVE is
 * produced today - a user either has a directory entry (ACTIVE) or it
 * doesn't exist (404).
 */
export type VoiceUserStatus = "ACTIVE";

/**
 * Mirrors `com.freeswitch.calling.model.VoiceUserSource`. Distinguishes
 * extensions this application provisioned from ones that already existed on
 * the FreeSWITCH server (e.g. 1001/1002).
 */
export type VoiceUserSource = "PROVISIONED_BY_API" | "EXISTING_EXTERNAL_USER";

/**
 * Response body shared by the create/get/list voice-user endpoints
 * (`VoiceUserResponse`). Never carries a password field.
 */
export interface VoiceUserResponse {
  extension: string;
  name: string;
  status: VoiceUserStatus;
  source: VoiceUserSource;
}

/** Request body for `POST /api/v1/voice/users` (`CreateVoiceUserRequest`). */
export interface CreateVoiceUserRequest {
  extension: string;
  password: string;
  name: string;
}

/** Response body for `DELETE /api/v1/voice/users/{extension}` (`DeleteVoiceUserResponse`). */
export interface DeleteVoiceUserResponse {
  extension: string;
  status: string;
}
