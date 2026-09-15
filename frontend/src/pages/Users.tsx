import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useUsers } from "../hooks/useUsers";
import { usersApi } from "../api/usersApi";
import { Loading } from "../components/Loading";
import { ErrorMessage } from "../components/ErrorMessage";
import { EmptyState } from "../components/EmptyState";
import { StatusBadge } from "../components/StatusBadge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { isProtectedExtension } from "../utils/formatters";
import { ApiRequestError } from "../types/api";
import type { VoiceUserResponse } from "../types/user";

export function Users() {
  const { users, loading, error, refresh } = useUsers();
  const [search, setSearch] = useState("");
  const [pendingDelete, setPendingDelete] = useState<VoiceUserResponse | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [banner, setBanner] = useState<string | null>(null);

  const filteredUsers = useMemo(() => {
    const needle = search.trim().toLowerCase();
    if (!needle) return users;
    return users.filter(
      (user) => user.extension.toLowerCase().includes(needle) || user.name.toLowerCase().includes(needle)
    );
  }, [users, search]);

  const provisionedCount = users.filter((user) => user.source === "PROVISIONED_BY_API").length;

  const handleDeleteConfirm = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await usersApi.deleteUser(pendingDelete.extension);
      setBanner(`Extension ${pendingDelete.extension} deleted.`);
      setPendingDelete(null);
      await refresh();
    } catch (err) {
      setDeleteError(err instanceof ApiRequestError ? err.message : "Failed to delete extension.");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Voice Users</h1>
          <p className="page-subtitle">
            {users.length} extension(s) - {provisionedCount} provisioned by this API.
          </p>
        </div>
        <div className="page-actions">
          <Link to="/users/create" className="btn btn-primary">
            Create User
          </Link>
        </div>
      </div>

      {banner && (
        <p className="success-banner" role="status">
          {banner}
        </p>
      )}
      {error && <ErrorMessage message={error} onRetry={refresh} />}
      {deleteError && <ErrorMessage message={deleteError} />}

      <div className="toolbar">
        <label className="field-inline">
          <span>Search</span>
          <input
            type="text"
            placeholder="Search by extension or name"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <button type="button" className="btn btn-secondary" onClick={refresh} disabled={loading}>
          {loading ? "Loading..." : "Refresh"}
        </button>
      </div>

      {loading && users.length === 0 ? (
        <Loading label="Loading users..." />
      ) : filteredUsers.length === 0 ? (
        users.length === 0 ? (
          <EmptyState
            title="No provisioned extensions found."
            description="Create your first extension."
            action={
              <Link to="/users/create" className="btn btn-primary">
                Create Extension
              </Link>
            }
          />
        ) : (
          <EmptyState title="No extensions match your search." />
        )
      ) : (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Extension</th>
                <th>Name</th>
                <th>Status</th>
                <th>Source</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredUsers.map((user) => {
                const protectedExtension = isProtectedExtension(user.extension) || user.source === "EXISTING_EXTERNAL_USER";
                return (
                  <tr key={user.extension}>
                    <td data-label="Extension" className="mono">
                      {user.extension}
                    </td>
                    <td data-label="Name">{user.name}</td>
                    <td data-label="Status">
                      <StatusBadge status={user.status} kind="user" />
                    </td>
                    <td data-label="Source">
                      <StatusBadge status={user.source} kind="source" />
                    </td>
                    <td data-label="Actions" className="actions-cell">
                      <Link to={`/users/${user.extension}`} className="btn btn-small">
                        View
                      </Link>
                      {!protectedExtension && (
                        <button
                          type="button"
                          className="btn btn-small btn-danger"
                          onClick={() => setPendingDelete(user)}
                        >
                          Delete
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title={`Delete extension ${pendingDelete?.extension ?? ""}?`}
        description="This action will remove the provisioned voice user."
        confirmLabel="Delete"
        danger
        loading={deleting}
        onConfirm={handleDeleteConfirm}
        onCancel={() => {
          setPendingDelete(null);
          setDeleteError(null);
        }}
      />
    </div>
  );
}
