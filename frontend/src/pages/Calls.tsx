import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useCallList } from "../hooks/useCalls";
import { StatusBadge } from "../components/StatusBadge";
import { EmptyState } from "../components/EmptyState";
import { ErrorMessage } from "../components/ErrorMessage";
import { Loading } from "../components/Loading";
import { formatDateTime } from "../utils/formatters";
import type { CallStatus } from "../types/call";

const STATUS_OPTIONS: Array<CallStatus | "ALL"> = ["ALL", "COMPLETED", "FAILED", "BUSY", "NO_ANSWER"];

export function Calls() {
  const { calls, loading, error, refresh } = useCallList();
  const [statusFilter, setStatusFilter] = useState<CallStatus | "ALL">("ALL");
  const [extensionFilter, setExtensionFilter] = useState("");

  const filteredCalls = useMemo(() => {
    const needle = extensionFilter.trim();
    return calls
      .filter((call) => statusFilter === "ALL" || call.status === statusFilter)
      .filter((call) => !needle || call.from.includes(needle) || call.to.includes(needle));
  }, [calls, statusFilter, extensionFilter]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Calls</h1>
          <p className="page-subtitle">
            Completed call history, most recent first. A call still in progress appears on its own details page
            until it ends.
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

        <button type="button" className="btn btn-secondary" onClick={refresh} disabled={loading}>
          {loading ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {error && <ErrorMessage message={error} onRetry={refresh} />}

      {loading && calls.length === 0 && <Loading label="Loading call history..." />}

      {!loading && filteredCalls.length === 0 ? (
        <EmptyState
          title="No calls found."
          description="Try adjusting your filters, or make a new call. Only completed calls appear here."
          action={
            <Link to="/calls/new" className="btn btn-primary">
              Make Call
            </Link>
          }
        />
      ) : (
        filteredCalls.length > 0 && (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Call ID</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Status</th>
                  <th>Started</th>
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
                    <td data-label="Started">{formatDateTime(call.createdAt)}</td>
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
        )
      )}
    </div>
  );
}
