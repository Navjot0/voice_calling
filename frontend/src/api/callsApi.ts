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
   * GET /api/v1/voice/calls - call history from the backend's `cdr` table,
   * most recently started first. Only calls that have already completed
   * appear here; an in-progress call isn't archived yet, so its live status
   * comes from `getCall` instead (see `useCallPolling`).
   */
  listCalls: async (): Promise<CallResponse[]> => {
    const { data } = await apiClient.get<CallResponse[]>("/calls");
    return data;
  },
};
