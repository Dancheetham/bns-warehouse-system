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

// APC's consumer tracker, apcchoice.apc-overnight.com - confirmed by Dan
// with a real working link: it needs the FULL 22-digit Hypaship WayBill as
// `id` (not the short 7-digit consignment number used elsewhere in this app
// for display/search - see apcWaybill on the order), plus the delivery
// postcode lowercased as `postcode` (e.g. "me4 4hy" -> "me4+4hy", the space
// becoming a literal "+" the same way URLSearchParams already encodes it).
// Confirmed example: https://apcchoice.apc-overnight.com/track-parcel?id=2026092908043390009308&postcode=me4+4hy
export function apcTrackingUrl(waybillNumber: string, postcode?: string): string {
  const params = new URLSearchParams({ id: waybillNumber });
  if (postcode) params.set("postcode", postcode.toLowerCase());
  return `https://apcchoice.apc-overnight.com/track-parcel?${params.toString()}`;
}
