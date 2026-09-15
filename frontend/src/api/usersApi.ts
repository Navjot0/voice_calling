import { apiClient } from "./client";
import type { CreateVoiceUserRequest, DeleteVoiceUserResponse, VoiceUserResponse } from "../types/user";

export const usersApi = {
  /** GET /api/v1/voice/users */
  listUsers: async (): Promise<VoiceUserResponse[]> => {
    const { data } = await apiClient.get<VoiceUserResponse[]>("/users");
    return data;
  },

  /** GET /api/v1/voice/users/{extension} */
  getUser: async (extension: string): Promise<VoiceUserResponse> => {
    const { data } = await apiClient.get<VoiceUserResponse>(`/users/${encodeURIComponent(extension)}`);
    return data;
  },

  /** POST /api/v1/voice/users */
  createUser: async (request: CreateVoiceUserRequest): Promise<VoiceUserResponse> => {
    const { data } = await apiClient.post<VoiceUserResponse>("/users", request);
    return data;
  },

  /** DELETE /api/v1/voice/users/{extension} */
  deleteUser: async (extension: string): Promise<DeleteVoiceUserResponse> => {
    const { data } = await apiClient.delete<DeleteVoiceUserResponse>(`/users/${encodeURIComponent(extension)}`);
    return data;
  },
};
