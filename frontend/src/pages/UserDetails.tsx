import { useCallback, useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { usersApi } from "../api/usersApi";
import { Loading } from "../components/Loading";
import { ErrorMessage } from "../components/ErrorMessage";
import { StatusBadge } from "../components/StatusBadge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { isProtectedExtension } from "../utils/formatters";
import { ApiRequestError } from "../types/api";
import type { VoiceUserResponse } from "../types/user";

interface LocationState {
  justCreated?: boolean;
}

export function UserDetails() {
  const { extension } = useParams<{ extension: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const justCreated = Boolean((location.state as LocationState | null)?.justCreated);

  const [user, setUser] = useState<VoiceUserResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!extension) return;
    setLoading(true);
    setError(null);
    try {
      const data = await usersApi.getUser(extension);
      setUser(data);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to load extension.");
    } finally {
      setLoading(false);
    }
  }, [extension]);

  useEffect(() => {
    load();
  }, [load]);

  const handleDelete = async () => {
    if (!extension) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await usersApi.deleteUser(extension);
      navigate("/users", { state: { deletedExtension: extension } });
    } catch (err) {
      setDeleteError(err instanceof ApiRequestError ? err.message : "Failed to delete extension.");
      setDeleting(false);
      setConfirmOpen(false);
    }
  };

  if (!extension) {
    return <ErrorMessage message="No extension was specified." />;
  }

  const protectedExtension = user
    ? isProtectedExtension(user.extension) || user.source === "EXISTING_EXTERNAL_USER"
    : false;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Extension {extension}</h1>
          <p className="page-subtitle">Voice user details</p>
        </div>
        <Link to="/users" className="btn btn-secondary">
          Back to Users
        </Link>
      </div>

      {justCreated && (
        <p className="success-banner" role="status">
          Extension created successfully.
        </p>
      )}
      {loading && <Loading label="Loading extension..." />}
      {error && <ErrorMessage message={error} onRetry={load} />}
      {deleteError && <ErrorMessage message={deleteError} />}

      {user && (
        <section className="panel">
          <dl className="detail-list">
            <div>
              <dt>Extension</dt>
              <dd className="mono">{user.extension}</dd>
            </div>
            <div>
              <dt>Name</dt>
              <dd>{user.name}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={user.status} kind="user" />
              </dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>
                <StatusBadge status={user.source} kind="source" />
              </dd>
            </div>
          </dl>

          {protectedExtension ? (
            <p className="protected-note">
              <strong>Protected Extension</strong> - this extension is permanently protected and cannot be deleted
              through the API.
            </p>
          ) : (
            <button type="button" className="btn btn-danger" onClick={() => setConfirmOpen(true)}>
              Delete Extension
            </button>
          )}
        </section>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title={`Delete extension ${extension}?`}
        description="This action will remove the provisioned voice user."
        confirmLabel="Delete"
        danger
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
