import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { callsApi } from "../api/callsApi";
import { useCallPolling } from "../hooks/useCalls";
import { ErrorMessage } from "../components/ErrorMessage";
import { StatusBadge } from "../components/StatusBadge";
import { ApiRequestError } from "../types/api";

const EXTENSION_PATTERN = /^[0-9]{2,15}$/;

interface FieldErrors {
  from?: string;
  to?: string;
}

export function MakeCall() {
  const [from, setFrom] = useState("1001");
  const [to, setTo] = useState("1002");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [activeCallId, setActiveCallId] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const { call: polledCall } = useCallPolling(activeCallId);

  const validate = (): boolean => {
    const errors: FieldErrors = {};
    if (!from.trim()) {
      errors.from = "From extension is required.";
    } else if (!EXTENSION_PATTERN.test(from.trim())) {
      errors.from = "Enter a numeric extension (2-15 digits).";
    }

    if (!to.trim()) {
      errors.to = "To extension is required.";
    } else if (!EXTENSION_PATTERN.test(to.trim())) {
      errors.to = "Enter a numeric extension (2-15 digits).";
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) {
      return; // Guards against duplicate submissions from a double click.
    }
    setSubmitError(null);
    setSuccessMessage(null);
    if (!validate()) {
      return;
    }

    setSubmitting(true);
    try {
      const response = await callsApi.createCall({ from: from.trim(), to: to.trim() });
      setActiveCallId(response.callId);
      setSuccessMessage("Call initiated successfully.");
    } catch (err) {
      setSubmitError(err instanceof ApiRequestError ? err.message : "Failed to start the call.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Make Call</h1>
          <p className="page-subtitle">Bridge two SIP extensions through FreeSWITCH.</p>
        </div>
      </div>

      <form className="form panel" onSubmit={handleSubmit} noValidate>
        {submitError && <ErrorMessage message={submitError} />}
        {successMessage && (
          <p className="success-banner" role="status">
            {successMessage}
          </p>
        )}

        <div className="form-field">
          <label htmlFor="from">From Extension</label>
          <input
            id="from"
            name="from"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
            aria-invalid={Boolean(fieldErrors.from)}
            aria-describedby={fieldErrors.from ? "from-error" : undefined}
          />
          {fieldErrors.from && (
            <p id="from-error" className="field-error">
              {fieldErrors.from}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="to">To Extension</label>
          <input
            id="to"
            name="to"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            value={to}
            onChange={(event) => setTo(event.target.value)}
            aria-invalid={Boolean(fieldErrors.to)}
            aria-describedby={fieldErrors.to ? "to-error" : undefined}
          />
          {fieldErrors.to && (
            <p id="to-error" className="field-error">
              {fieldErrors.to}
            </p>
          )}
        </div>

        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? "Starting call..." : "Make Call"}
          </button>
        </div>
      </form>

      {activeCallId && polledCall && (
        <section className="panel">
          <h2>Call Status</h2>
          <dl className="detail-list">
            <div>
              <dt>Call ID</dt>
              <dd className="mono">{polledCall.callId}</dd>
            </div>
            <div>
              <dt>From</dt>
              <dd>{polledCall.from}</dd>
            </div>
            <div>
              <dt>To</dt>
              <dd>{polledCall.to}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={polledCall.status} kind="call" />
              </dd>
            </div>
          </dl>
          <Link to={`/calls/${activeCallId}`} className="btn btn-secondary">
            View full details
          </Link>
        </section>
      )}
    </div>
  );
}
