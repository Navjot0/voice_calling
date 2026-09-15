interface ErrorMessageProps {
  message: string;
  onRetry?: () => void;
}

/**
 * Renders a user-friendly error banner. `message` should already be a safe,
 * human-readable string (see `api/client.ts`) - this component never
 * receives or displays raw stack traces or backend internals.
 */
export function ErrorMessage({ message, onRetry }: ErrorMessageProps) {
  return (
    <div className="error-message" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="btn btn-secondary btn-small" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
