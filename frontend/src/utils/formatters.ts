const DEFAULT_PROTECTED_EXTENSIONS = ["1001", "1002"];

/**
 * The backend enforces protected extensions server-side
 * (`freeswitch.directory.protected-extensions`, default `1001,1002`) and
 * returns `403 VOICE_USER_PROTECTED` if the UI ever gets this wrong - so
 * this is only used to decide whether to show a Delete button, never as the
 * source of truth for whether deletion actually succeeds.
 */
export function getProtectedExtensions(): string[] {
  const raw = import.meta.env.VITE_PROTECTED_EXTENSIONS;
  if (!raw) {
    return DEFAULT_PROTECTED_EXTENSIONS;
  }
  const parsed = raw
    .split(",")
    .map((extension) => extension.trim())
    .filter(Boolean);
  return parsed.length > 0 ? parsed : DEFAULT_PROTECTED_EXTENSIONS;
}

export function isProtectedExtension(extension: string): boolean {
  return getProtectedExtensions().includes(extension);
}

/** Formats an ISO-8601 timestamp for display, or an em dash if absent/invalid. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }
  return date.toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "medium",
  });
}

/** Formats a whole-second duration as "Xm Ys" (or "Ys" under a minute), or an em dash if absent. */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || Number.isNaN(seconds)) {
    return "—";
  }
  const total = Math.max(0, Math.trunc(seconds));
  const minutes = Math.floor(total / 60);
  const remainingSeconds = total % 60;
  return minutes > 0 ? `${minutes}m ${remainingSeconds}s` : `${remainingSeconds}s`;
}
