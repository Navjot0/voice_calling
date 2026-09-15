import { useCallback, useEffect, useRef, useState } from "react";
import { callsApi } from "../api/callsApi";
import { ApiRequestError } from "../types/api";
import { isTerminalCallStatus, type CallDirection, type CallResponse, type CallStatus } from "../types/call";

const POLL_INTERVAL_MS = 2500;

interface UseCallPollingResult {
  call: CallResponse | null;
  loading: boolean;
  error: string | null;
}

/**
 * Polls `GET /api/v1/voice/calls/{callId}` every ~2.5s while the call is in
 * a non-terminal state, and stops automatically once it reaches COMPLETED,
 * FAILED, BUSY or NO_ANSWER. Each hook instance owns exactly one interval,
 * cleared before a new one is ever created and on unmount/callId change -
 * so there is never more than one active timer per call.
 */
export function useCallPolling(callId: string | null): UseCallPollingResult {
  const [call, setCall] = useState<CallResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const timerRef = useRef<number | null>(null);
  const activeCallIdRef = useRef<string | null>(null);

  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const fetchOnce = useCallback(
    async (id: string) => {
      try {
        const data = await callsApi.getCall(id);
        if (activeCallIdRef.current !== id) {
          return; // A newer callId took over while this request was in flight.
        }
        setCall(data);
        setError(null);
        setLoading(false);
        if (isTerminalCallStatus(data.status)) {
          clearTimer();
        }
      } catch (err) {
        if (activeCallIdRef.current !== id) {
          return;
        }
        setError(err instanceof ApiRequestError ? err.message : "Failed to load call status.");
        setLoading(false);
        clearTimer();
      }
    },
    [clearTimer]
  );

  useEffect(() => {
    activeCallIdRef.current = callId;
    clearTimer();
    setCall(null);
    setError(null);

    if (!callId) {
      setLoading(false);
      return;
    }

    setLoading(true);
    fetchOnce(callId);
    timerRef.current = window.setInterval(() => fetchOnce(callId), POLL_INTERVAL_MS);

    return () => {
      clearTimer();
    };
  }, [callId, fetchOnce, clearTimer]);

  return { call, loading, error };
}

const HISTORY_STORAGE_KEY = "extension-calling.call-history.v1";
const MAX_HISTORY_ENTRIES = 100;

export interface TrackedCall {
  callId: string;
  from: string;
  to: string;
  direction: CallDirection;
  status: CallStatus;
  createdAt: string;
}

function readHistory(): TrackedCall[] {
  try {
    const raw = window.localStorage.getItem(HISTORY_STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as TrackedCall[]) : [];
  } catch {
    return [];
  }
}

function writeHistory(entries: TrackedCall[]): void {
  try {
    window.localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_HISTORY_ENTRIES)));
  } catch {
    // Best-effort only: browser storage may be unavailable (private mode, quota exceeded, etc).
  }
}

interface UseCallHistoryResult {
  history: TrackedCall[];
  recordCall: (call: Omit<TrackedCall, "createdAt"> & { createdAt?: string }) => void;
  updateCallStatus: (callId: string, status: CallStatus) => void;
}

/**
 * Tracks calls created from this browser in localStorage (never a password
 * or other secret - calls have none). The backend does not currently expose
 * a call-list endpoint (see `callsApi.listCalls`), so this is a client-side
 * call log scoped to this browser, not a full server-side call history -
 * pages using it say so in their copy.
 */
export function useCallHistory(): UseCallHistoryResult {
  const [history, setHistory] = useState<TrackedCall[]>(() => readHistory());

  const recordCall = useCallback<UseCallHistoryResult["recordCall"]>((call) => {
    setHistory((prev) => {
      const next: TrackedCall[] = [
        { ...call, createdAt: call.createdAt ?? new Date().toISOString() },
        ...prev.filter((entry) => entry.callId !== call.callId),
      ];
      writeHistory(next);
      return next;
    });
  }, []);

  const updateCallStatus = useCallback((callId: string, status: CallStatus) => {
    setHistory((prev) => {
      let changed = false;
      const next = prev.map((entry) => {
        if (entry.callId === callId && entry.status !== status) {
          changed = true;
          return { ...entry, status };
        }
        return entry;
      });
      if (changed) {
        writeHistory(next);
        return next;
      }
      return prev;
    });
  }, []);

  return { history, recordCall, updateCallStatus };
}
