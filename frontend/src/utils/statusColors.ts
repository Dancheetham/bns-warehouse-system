import { OrderStatus } from "../types";

// Matches the hex values of the Tailwind *-100 shades this page used to use
// directly, so switching to customisable colours doesn't change anything
// visually until someone actually customises it.
export const DEFAULT_STATUS_COLORS: Record<OrderStatus, string> = {
  ON_HOLD: "#fef3c7",
  AWAITING_DESPATCH: "#cffafe",
  CANCELLED: "#fee2e2",
  COMPLETED: "#d1fae5",
  PARTIALLY_DESPATCHED: "#dbeafe",
  AWAITING_CONVERSION: "#f3e8ff",
  // Despatched, waiting on Generate Invoices - a distinct amber-ish tone,
  // deliberately different from COMPLETED's green, since it's a step that
  // still needs someone to act on it.
  INVOICE_PENDING: "#fef9c3",
};

export const ORDER_STATUSES: OrderStatus[] = [
  "ON_HOLD",
  "AWAITING_DESPATCH",
  "PARTIALLY_DESPATCHED",
  "INVOICE_PENDING",
  "COMPLETED",
  "CANCELLED",
  "AWAITING_CONVERSION",
];

export function statusColorSettingKey(status: OrderStatus): string {
  return `status_color_${status}`;
}

export function statusLabel(status: OrderStatus): string {
  return status.replace(/_/g, " ");
}

/** Reads customised colours out of the generic settings map, falling back to defaults. */
export function resolveStatusColors(settings: Record<string, string> | undefined): Record<OrderStatus, string> {
  const colors = { ...DEFAULT_STATUS_COLORS };
  if (!settings) return colors;
  for (const status of ORDER_STATUSES) {
    const custom = settings[statusColorSettingKey(status)];
    if (custom) colors[status] = custom;
  }
  return colors;
}
