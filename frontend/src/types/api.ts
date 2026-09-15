/**
 * Mirrors the backend's `com.freeswitch.calling.exception.ApiError` record -
 * the single error shape returned by every failed request
 * (`GlobalExceptionHandler`).
 */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

/**
 * Thrown by the Axios client (see `api/client.ts`) for every failed request,
 * so every call site can catch one error type regardless of whether the
 * failure came from the backend, the network, or something unexpected.
 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly errorCode: string;
  readonly path?: string;
  readonly timestamp?: string;

  constructor(details: { message: string; status: number; errorCode: string; path?: string; timestamp?: string }) {
    super(details.message);
    this.name = "ApiRequestError";
    this.status = details.status;
    this.errorCode = details.errorCode;
    this.path = details.path;
    this.timestamp = details.timestamp;
  }
}
