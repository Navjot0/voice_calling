import { useCallback, useEffect, useRef, useState } from "react";
import { callsApi } from "../api/callsApi";
import { ApiRequestError } from "../types/api";
import { isTerminalCallStatus, type CallResponse } from "../types/call";

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

interface UseCallListResult {
  calls: CallResponse[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

/**
 * Fetches `GET /api/v1/voice/calls` - real call history from the backend's
 * `cdr` table, most recently started first. Only calls that have already
 * completed appear here; a call still in progress isn't archived yet (see
 * the backend's `PersistentCallRepository`), so it won't show up until it
 * hangs up - `useCallPolling` is what tracks a single call's live status.
 */
export function useCallList(): UseCallListResult {
  const [calls, setCalls] = useState<CallResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchList = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await callsApi.listCalls();
      setCalls(data);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to load call history.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  return { calls, loading, error, refresh: fetchList };
}

/**
 * Whole seconds elapsed since `startIso`, ticking up once a second for as
 * long as `active` is true - so a live call's duration visibly counts up
 * between each ~2.5s `useCallPolling` refresh instead of only jumping every
 * few seconds. Returns `null` once `active` goes false (show the call's
 * final `duration`/`billsec` from the API instead) or while `startIso`
 * isn't known yet.
 */
export function useElapsedSeconds(startIso: string | null | undefined, active: boolean): number | null {
  const [elapsed, setElapsed] = useState<number | null>(null);

  useEffect(() => {
    if (!startIso || !active) {
      setElapsed(null);
      return;
    }
    const startMs = new Date(startIso).getTime();
    if (Number.isNaN(startMs)) {
      setElapsed(null);
      return;
    }

    const tick = () => setElapsed(Math.max(0, Math.floor((Date.now() - startMs) / 1000)));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [startIso, active]);

  return elapsed;
}
