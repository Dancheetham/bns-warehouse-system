package uk.co.bns.warehouse_api.dto;

import java.util.List;

/**
 * Response for the GDMS channel dropdown on Companies.tsx - same live/cached
 * shape as DpdServiceLookupResult. `live` is true only when this list came
 * straight from GDMS's own /sub/list just now; when that call fails,
 * `channels` falls back to the last list GDMS returned successfully
 * (cached in Settings), with `live` false and `liveError` carrying why.
 */
public record GdmsChannelLookupResult(List<GdmsChannelOption> channels, boolean live, String liveError) {}
