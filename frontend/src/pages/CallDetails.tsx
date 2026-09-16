import { Link, useParams } from "react-router-dom";
import { useCallPolling } from "../hooks/useCalls";
import { Loading } from "../components/Loading";
import { ErrorMessage } from "../components/ErrorMessage";
import { StatusBadge } from "../components/StatusBadge";
import { formatDateTime, formatDuration } from "../utils/formatters";
import { isTerminalCallStatus } from "../types/call";

export function CallDetails() {
  const { callId } = useParams<{ callId: string }>();
  const { call, loading, error } = useCallPolling(callId ?? null);

  if (!callId) {
    return <ErrorMessage message="No call ID was provided." />;
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Call Details</h1>
          <p className="page-subtitle mono">{callId}</p>
        </div>
        <Link to="/calls" className="btn btn-secondary">
          Back to Calls
        </Link>
      </div>

      {loading && !call && <Loading label="Loading call status..." />}
      {error && <ErrorMessage message={error} />}

      {call && (
        <section className="panel">
          <dl className="detail-list">
            <div>
              <dt>Call ID</dt>
              <dd className="mono">{call.callId}</dd>
            </div>
            <div>
              <dt>From</dt>
              <dd>{call.from}</dd>
            </div>
            <div>
              <dt>To</dt>
              <dd>{call.to}</dd>
            </div>
            <div>
              <dt>Direction</dt>
              <dd>{call.direction}</dd>
            </div>
            <div>
              <dt>Current Status</dt>
              <dd>
                <StatusBadge status={call.status} kind="call" />
              </dd>
            </div>
            <div>
              <dt>Created At</dt>
              <dd>{formatDateTime(call.createdAt)}</dd>
            </div>
            <div>
              <dt>Answered At</dt>
              <dd>{formatDateTime(call.answeredAt)}</dd>
            </div>
            <div>
              <dt>Completed At</dt>
              <dd>{formatDateTime(call.completedAt)}</dd>
            </div>
            <div>
              <dt>Duration</dt>
              <dd>{formatDuration(call.duration)}</dd>
            </div>
            <div>
              <dt>Billsec</dt>
              <dd>{formatDuration(call.billsec)}</dd>
            </div>
          </dl>

          {!isTerminalCallStatus(call.status) && (
            <p className="panel-note">This call is still in progress - status updates automatically every few seconds.</p>
          )}
        </section>
      )}
    </div>
  );
}
