type BadgeTone = "neutral" | "info" | "success" | "warning" | "danger";
type BadgeKind = "call" | "user" | "source";

const CALL_STATUS_TONE: Record<string, BadgeTone> = {
  INITIATED: "info",
  RINGING: "info",
  ANSWERED: "success",
  COMPLETED: "success",
  FAILED: "danger",
  BUSY: "warning",
  NO_ANSWER: "warning",
};

const USER_STATUS_TONE: Record<string, BadgeTone> = {
  ACTIVE: "success",
  DELETED: "neutral",
};

const SOURCE_TONE: Record<string, BadgeTone> = {
  PROVISIONED_BY_API: "info",
  EXISTING_EXTERNAL_USER: "neutral",
};

// The backend's raw enum value is EXISTING_EXTERNAL_USER; the UI must show
// this as "PRE-EXISTING" so provisioned vs. pre-existing extensions read
// clearly at a glance (see the Voice Users table).
const SOURCE_LABEL: Record<string, string> = {
  PROVISIONED_BY_API: "PROVISIONED_BY_API",
  EXISTING_EXTERNAL_USER: "PRE-EXISTING",
};

const TONE_BY_KIND: Record<BadgeKind, Record<string, BadgeTone>> = {
  call: CALL_STATUS_TONE,
  user: USER_STATUS_TONE,
  source: SOURCE_TONE,
};

interface StatusBadgeProps {
  status: string;
  kind?: BadgeKind;
}

/** Reusable status pill with a distinct visual treatment per status/kind. */
export function StatusBadge({ status, kind = "call" }: StatusBadgeProps) {
  const tone = TONE_BY_KIND[kind][status] ?? "neutral";
  const label = kind === "source" ? SOURCE_LABEL[status] ?? status : status.replace(/_/g, " ");
  return <span className={`badge badge-${tone}`}>{label}</span>;
}
