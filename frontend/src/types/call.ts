/**
 * Mirrors `com.freeswitch.calling.model.CallStatus` exactly. A call does not
 * necessarily pass through every state: a successful flow moves
 * INITIATED -> RINGING -> ANSWERED -> COMPLETED, while an unsuccessful one
 * terminates instead in FAILED, BUSY or NO_ANSWER.
 */
export type CallStatus =
  | "INITIATED"
  | "RINGING"
  | "ANSWERED"
  | "COMPLETED"
  | "FAILED"
  | "BUSY"
  | "NO_ANSWER";

/** Mirrors `com.freeswitch.calling.model.CallDirection`. */
export type CallDirection = "INBOUND" | "OUTBOUND";

const TERMINAL_CALL_STATUSES: readonly CallStatus[] = ["COMPLETED", "FAILED", "BUSY", "NO_ANSWER"];

export function isTerminalCallStatus(status: CallStatus): boolean {
  return TERMINAL_CALL_STATUSES.includes(status);
}

/** Request body for `POST /api/v1/voice/calls` (`CreateCallRequest`). */
export interface CreateCallRequest {
  from: string;
  to: string;
}

/**
 * Response body for `POST /api/v1/voice/calls` (`CreateCallResponse`).
 * `status` only reflects that FreeSWITCH accepted the originate command
 * (INITIATED) - it is not confirmation that anyone answered.
 */
export interface CreateCallResponse {
  callId: string;
  status: CallStatus;
  from: string;
  to: string;
  direction: CallDirection;
}

/**
 * Response body for `GET /api/v1/voice/calls/{callId}` (`CallResponse`).
 * Timestamps are ISO-8601 strings (Java `Instant` serialized by Jackson).
 * `answeredAt` / `completedAt` are `null` until the call reaches that point.
 * `duration` (total seconds, start to end) and `billsec` (billable seconds,
 * answer to end - `0` if never answered) are `null` until the call ends.
 */
export interface CallResponse {
  callId: string;
  status: CallStatus;
  from: string;
  to: string;
  direction: CallDirection;
  createdAt: string;
  answeredAt: string | null;
  completedAt: string | null;
  duration: number | null;
  billsec: number | null;
}
