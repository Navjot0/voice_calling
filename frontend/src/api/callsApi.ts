import { apiClient } from "./client";
import type { CallResponse, CreateCallRequest, CreateCallResponse } from "../types/call";

export const callsApi = {
  /** POST /api/v1/voice/calls */
  createCall: async (request: CreateCallRequest): Promise<CreateCallResponse> => {
    const { data } = await apiClient.post<CreateCallResponse>("/calls", request);
    return data;
  },

  /** GET /api/v1/voice/calls/{callId} */
  getCall: async (callId: string): Promise<CallResponse> => {
    const { data } = await apiClient.get<CallResponse>(`/calls/${encodeURIComponent(callId)}`);
    return data;
  },

  /**
   * The backend does not currently expose `GET /api/v1/voice/calls` (a list
   * endpoint) - see VoiceCallController. This is intentionally unused today;
   * it exists so the Calls page can be pointed at a real list with a
   * one-line change once the backend adds one, instead of the frontend
   * inventing an endpoint that doesn't exist.
   */
  listCalls: async (): Promise<CallResponse[]> => {
    const { data } = await apiClient.get<CallResponse[]>("/calls");
    return data;
  },
};
