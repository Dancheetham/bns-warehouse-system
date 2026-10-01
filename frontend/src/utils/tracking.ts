// DPD's own public tracking search - the only courier wired up today (see
// DpdShippingService). A postcode isn't strictly required by DPD's tracker,
// but including it when known narrows the search straight to the right
// parcel rather than a list of near-matches. Centralised here rather than
// inlined at each call site so there's exactly one place to update if DPD
// ever changes this URL, or when a second courier is added later.
export function dpdTrackingUrl(consignmentNumber: string, postcode?: string): string {
  const params = new URLSearchParams({ reference: consignmentNumber });
  if (postcode) params.set("postcode", postcode);
  return `https://track.dpd.co.uk/search?${params.toString()}`;
}

// APC's consumer tracker, apcchoice.apc-overnight.com - takes the short
// 7-digit consignment number (the last 7 digits of the full Hypaship
// WayBill - see apcWaybill on the order, and the comment next to it in
// OrderEdit.tsx) plus the delivery postcode. NOTE: unlike dpdTrackingUrl
// above, this exact query-string format (id + postcode) is a best guess
// from the tracker's own URL pattern and in-page form fields, not something
// confirmed end-to-end against a real waybill yet - it's a client-rendered
// page, so it couldn't be verified by fetching it directly. Worth Dan
// confirming on the next real APC tracking click; if the deep link doesn't
// land on the right parcel, the fix is almost certainly just the param
// name/format here, not anything else in this app.
export function apcTrackingUrl(consignmentNumber: string, postcode?: string): string {
  const params = new URLSearchParams({ id: consignmentNumber });
  if (postcode) params.set("postcode", postcode);
  return `https://apcchoice.apc-overnight.com/track-parcel?${params.toString()}`;
}
