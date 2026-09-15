import { useCallback, useEffect, useState } from "react";
import { usersApi } from "../api/usersApi";
import { ApiRequestError } from "../types/api";
import type { VoiceUserResponse } from "../types/user";

interface UseUsersResult {
  users: VoiceUserResponse[];
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
}

/** Loads and refreshes the voice-user list from GET /api/v1/voice/users. */
export function useUsers(): UseUsersResult {
  const [users, setUsers] = useState<VoiceUserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await usersApi.listUsers();
      setUsers(data);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to load voice users.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { users, loading, error, refresh };
}
