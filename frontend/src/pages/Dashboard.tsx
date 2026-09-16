import { Link } from "react-router-dom";
import { useUsers } from "../hooks/useUsers";
import { useCallList } from "../hooks/useCalls";
import { Loading } from "../components/Loading";
import { ErrorMessage } from "../components/ErrorMessage";
import { StatusBadge } from "../components/StatusBadge";
import { EmptyState } from "../components/EmptyState";
import { formatDateTime, formatDuration, isProtectedExtension } from "../utils/formatters";

export function Dashboard() {
  const { users, loading: usersLoading, error: usersError, refresh: refreshUsers } = useUsers();
  const { calls, loading: callsLoading, error: callsError, refresh: refreshCalls } = useCallList();

  const totalExtensions = users.length;
  const protectedExtensions = users.filter((user) => isProtectedExtension(user.extension)).length;
  const provisionedExtensions = users.filter((user) => user.source === "PROVISIONED_BY_API").length;

  const totalCalls = calls.length;
  const completedCalls = calls.filter((call) => call.status === "COMPLETED").length;
  const failedCalls = calls.filter(
    (call) => call.status === "FAILED" || call.status === "BUSY" || call.status === "NO_ANSWER"
  ).length;

  const recentCalls = calls.slice(0, 5);

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
        <StatTile label="Total Calls" value={callsLoading ? "…" : totalCalls} />
        <StatTile label="Completed Calls" value={callsLoading ? "…" : completedCalls} />
        <StatTile label="Failed / Unanswered Calls" value={callsLoading ? "…" : failedCalls} />
      </section>

      <section className="panel">
        <div className="panel-header">
          <h2>Recent Calls</h2>
          <Link to="/calls">View all</Link>
        </div>
        <p className="panel-note">
          Most recently completed calls. A call still in progress appears here once it ends.
        </p>

        {callsError && <ErrorMessage message={callsError} onRetry={refreshCalls} />}
        {callsLoading && calls.length === 0 && <Loading label="Loading recent calls..." />}

        {!callsLoading && recentCalls.length === 0 ? (
          <EmptyState
            title="No calls found."
            description="Make your first call to see it appear here once it completes."
            action={
              <Link to="/calls/new" className="btn btn-primary">
                Make Call
              </Link>
            }
          />
        ) : (
          recentCalls.length > 0 && (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Call ID</th>
                    <th>From</th>
                    <th>To</th>
                    <th>Status</th>
                    <th>Started</th>
                    <th>Duration</th>
                    <th>Billsec</th>
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
                      <td data-label="Started">{formatDateTime(call.createdAt)}</td>
                      <td data-label="Duration">{formatDuration(call.duration)}</td>
                      <td data-label="Billsec">{formatDuration(call.billsec)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
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
