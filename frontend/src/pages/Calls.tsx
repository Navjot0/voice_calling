import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useCallHistory } from "../hooks/useCalls";
import { callsApi } from "../api/callsApi";
import { StatusBadge } from "../components/StatusBadge";
import { EmptyState } from "../components/EmptyState";
import { ErrorMessage } from "../components/ErrorMessage";
import { formatDateTime } from "../utils/formatters";
import { isTerminalCallStatus, type CallStatus } from "../types/call";

const STATUS_OPTIONS: Array<CallStatus | "ALL"> = [
  "ALL",
  "INITIATED",
  "RINGING",
  "ANSWERED",
  "COMPLETED",
  "FAILED",
  "BUSY",
  "NO_ANSWER",
];

export function Calls() {
  const { history, updateCallStatus } = useCallHistory();
  const [statusFilter, setStatusFilter] = useState<CallStatus | "ALL">("ALL");
  const [extensionFilter, setExtensionFilter] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  const filteredCalls = useMemo(() => {
    const needle = extensionFilter.trim();
    return [...history]
      .filter((call) => statusFilter === "ALL" || call.status === statusFilter)
      .filter((call) => !needle || call.from.includes(needle) || call.to.includes(needle))
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [history, statusFilter, extensionFilter]);

  const handleRefresh = async () => {
    setRefreshing(true);
    setRefreshError(null);
    const nonTerminal = history.filter((call) => !isTerminalCallStatus(call.status));
    const results = await Promise.allSettled(
      nonTerminal.map(async (call) => {
        const latest = await callsApi.getCall(call.callId);
        updateCallStatus(call.callId, latest.status);
      })
    );
    if (results.some((result) => result.status === "rejected")) {
      setRefreshError("Some calls could not be refreshed. They may still be in progress.");
    }
    setRefreshing(false);
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Calls</h1>
          <p className="page-subtitle">
            Calls initiated from this browser. Wire this up to a backend list endpoint once one exists.
          </p>
        </div>
        <div className="page-actions">
          <Link to="/calls/new" className="btn btn-primary">
            Make Call
          </Link>
        </div>
      </div>

      <div className="toolbar">
        <label className="field-inline">
          <span>Status</span>
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as CallStatus | "ALL")}>
            {STATUS_OPTIONS.map((status) => (
              <option key={status} value={status}>
                {status === "ALL" ? "All statuses" : status}
              </option>
            ))}
          </select>
        </label>

        <label className="field-inline">
          <span>Extension</span>
          <input
            type="text"
            placeholder="Filter by extension"
            value={extensionFilter}
            onChange={(event) => setExtensionFilter(event.target.value)}
          />
        </label>

        <button type="button" className="btn btn-secondary" onClick={handleRefresh} disabled={refreshing}>
          {refreshing ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {refreshError && <ErrorMessage message={refreshError} />}

      {filteredCalls.length === 0 ? (
        <EmptyState
          title="No calls found."
          description="Try adjusting your filters, or make a new call."
          action={
            <Link to="/calls/new" className="btn btn-primary">
              Make Call
            </Link>
          }
        />
      ) : (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Call ID</th>
                <th>From</th>
                <th>To</th>
                <th>Status</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredCalls.map((call) => (
                <tr key={call.callId}>
                  <td data-label="Call ID" className="mono">
                    {call.callId}
                  </td>
                  <td data-label="From">{call.from}</td>
                  <td data-label="To">{call.to}</td>
                  <td data-label="Status">
                    <StatusBadge status={call.status} kind="call" />
                  </td>
                  <td data-label="Created">{formatDateTime(call.createdAt)}</td>
                  <td data-label="Actions">
                    <Link to={`/calls/${call.callId}`} className="btn btn-small">
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
