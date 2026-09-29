package uk.co.bns.warehouse_api.dto;

/**
 * One GDMS "channel" - a subordinate reseller account under BNS's own GDMS
 * account, as returned by /sub/list. id is what's sent back to GDMS on
 * /assign; name is what the Companies.tsx dropdown shows.
 */
public record GdmsChannelOption(String id, String name) {}
