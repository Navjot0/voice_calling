import { Link } from "react-router-dom";
import { useUsers } from "../hooks/useUsers";
import { useCallHistory } from "../hooks/useCalls";
import { Loading } from "../components/Loading";
import { ErrorMessage } from "../components/ErrorMessage";
import { StatusBadge } from "../components/StatusBadge";
import { EmptyState } from "../components/EmptyState";
import { formatDateTime, isProtectedExtension } from "../utils/formatters";
import { isTerminalCallStatus } from "../types/call";

export function Dashboard() {
  const { users, loading: usersLoading, error: usersError, refresh: refreshUsers } = useUsers();
  const { history } = useCallHistory();

  const totalExtensions = users.length;
  const protectedExtensions = users.filter((user) => isProtectedExtension(user.extension)).length;
  const provisionedExtensions = users.filter((user) => user.source === "PROVISIONED_BY_API").length;

  const activeCalls = history.filter((call) => !isTerminalCallStatus(call.status)).length;
  const completedCalls = history.filter((call) => call.status === "COMPLETED").length;
  const failedCalls = history.filter((call) => call.status === "FAILED" || call.status === "BUSY" || call.status === "NO_ANSWER").length;

  const recentCalls = [...history]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p className="page-subtitle">Live snapshot of voice users and calling activity.</p>
        </div>
        <div className="page-actions">
          <Link to="/calls/new" className="btn btn-primary">
            Make Call
          </Link>
          <Link to="/users/create" className="btn btn-secondary">
            Create Extension
          </Link>
        </div>
      </div>

      {usersError && <ErrorMessage message={usersError} onRetry={refreshUsers} />}
      {usersLoading && <Loading label="Loading users..." />}

      <section className="stat-grid" aria-label="Extension and call metrics">
        <StatTile label="Total Extensions" value={usersLoading ? "…" : totalExtensions} />
        <StatTile label="Protected Extensions" value={usersLoading ? "…" : protectedExtensions} />
        <StatTile label="Provisioned Extensions" value={usersLoading ? "…" : provisionedExtensions} />
        <StatTile label="Active Calls" value={activeCalls} />
        <StatTile label="Completed Calls" value={completedCalls} />
        <StatTile label="Failed / Unanswered Calls" value={failedCalls} />
      </section>

      <section className="panel">
        <div className="panel-header">
          <h2>Recent Calls</h2>
          <Link to="/calls">View all</Link>
        </div>
        <p className="panel-note">
          Calls created from this browser. The backend does not yet expose a call-history endpoint, so this list
          reflects local activity in this browser only.
        </p>

        {recentCalls.length === 0 ? (
          <EmptyState
            title="No calls found."
            description="Make your first call to see it appear here."
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
                </tr>
              </thead>
              <tbody>
                {recentCalls.map((call) => (
                  <tr key={call.callId}>
                    <td data-label="Call ID" className="mono">
                      <Link to={`/calls/${call.callId}`}>{call.callId}</Link>
                    </td>
                    <td data-label="From">{call.from}</td>
                    <td data-label="To">{call.to}</td>
                    <td data-label="Status">
                      <StatusBadge status={call.status} kind="call" />
                    </td>
                    <td data-label="Created">{formatDateTime(call.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function StatTile({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="stat-tile">
      <p className="stat-tile-value">{value}</p>
      <p className="stat-tile-label">{label}</p>
    </div>
  );
}
