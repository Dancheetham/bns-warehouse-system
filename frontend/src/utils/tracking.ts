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
