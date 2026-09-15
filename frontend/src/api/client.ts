import axios, { type AxiosError } from "axios";
import { ApiRequestError, type ApiErrorBody } from "../types/api";

const rawBaseUrl = import.meta.env.VITE_API_BASE_URL;

if (!rawBaseUrl) {
  // Fail loudly in the console rather than silently issuing requests
  // against a relative path that only happens to work by coincidence.
  console.error(
    "VITE_API_BASE_URL is not set. Copy .env.example to .env and point it at your backend (e.g. http://localhost:8080)."
  );
}

const normalizedBaseUrl = (rawBaseUrl ?? "").replace(/\/+$/, "");

/**
 * Single Axios instance used by every API module. Base URL is
 * `${VITE_API_BASE_URL}/api/v1/voice` - never hard-coded, and never pointed
 * at FreeSWITCH directly (this app only ever talks to the Spring Boot
 * backend over REST).
 */
export const apiClient = axios.create({
  baseURL: `${normalizedBaseUrl}/api/v1/voice`,
  timeout: 15000,
  headers: {
    "Content-Type": "application/json",
  },
});

const FALLBACK_MESSAGES_BY_STATUS: Record<number, string> = {
  400: "The request was invalid. Please check the form and try again.",
  403: "This operation is not allowed.",
  404: "The requested resource could not be found.",
  409: "This resource already exists.",
  500: "Something went wrong on the server. Please try again.",
  502: "The voice platform is temporarily unavailable. Please try again shortly.",
  503: "The voice platform is temporarily unavailable. Please try again shortly.",
};

function toApiRequestError(error: AxiosError<Partial<ApiErrorBody>>): ApiRequestError {
  if (error.response) {
    const body = error.response.data;
    const status = error.response.status;
    const message =
      (body && typeof body.message === "string" && body.message.trim().length > 0 && body.message) ||
      FALLBACK_MESSAGES_BY_STATUS[status] ||
      "Something went wrong while talking to the voice platform.";

    return new ApiRequestError({
      message,
      status,
      errorCode: body?.error ?? "UNKNOWN_ERROR",
      path: body?.path,
      timestamp: body?.timestamp,
    });
  }

  if (error.request) {
    return new ApiRequestError({
      message:
        "Could not reach the backend API. Check that it is running and that VITE_API_BASE_URL is configured correctly.",
      status: 0,
      errorCode: "NETWORK_ERROR",
    });
  }

  return new ApiRequestError({
    message: error.message || "An unexpected error occurred.",
    status: 0,
    errorCode: "UNKNOWN_ERROR",
  });
}

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<Partial<ApiErrorBody>>) => Promise.reject(toApiRequestError(error))
);
