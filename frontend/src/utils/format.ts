// Centralised UK date formatting. Using toLocaleDateString("en-GB") explicitly
// (rather than no-arg toLocaleDateString()/toLocaleString()) guarantees DD/MM/YYYY
// regardless of what locale the browser or OS happens to be set to.

export function formatDate(value: string | Date): string {
  const date = typeof value === "string" ? new Date(value) : value;
  return date.toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

export function formatDateTime(value: string | Date): string {
  const date = typeof value === "string" ? new Date(value) : value;
  return date.toLocaleString("en-GB", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

// Client-side date-range filtering, used by every list page that already
// pulls its whole dataset into the browser (Sales Activity, Invoice
// History, Payment Tracking, RMAs, Purchase Orders) rather than paging or
// filtering server-side - consistent with how those pages already do their
// text search entirely in the browser. `from`/`to` are plain yyyy-mm-dd
// values straight out of a <input type="date">; either can be blank to
// leave that end of the range open. Compares by calendar day, not exact
// timestamp, so a `to` date includes everything that happened on that day.
export function inDateRange(value: string | Date | null | undefined, from: string, to: string): boolean {
  if (!from && !to) return true;
  if (!value) return false;
  const date = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return false;
  if (from && date < new Date(from + "T00:00:00")) return false;
  if (to && date > new Date(to + "T23:59:59.999")) return false;
  return true;
}
