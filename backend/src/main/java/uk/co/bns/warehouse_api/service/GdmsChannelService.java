package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.GdmsChannelLookupResult;
import uk.co.bns.warehouse_api.dto.GdmsChannelOption;
import uk.co.bns.warehouse_api.exception.ValidationException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The GDMS Channel Management calls BNS actually needs: listing the
 * subordinate channels (reseller accounts) under BNS's own GDMS account, and
 * assigning despatched devices' MACs to one of them - see
 * gdms-api-findings.md for the full endpoint reference this is built from.
 *
 * Every call here goes through GdmsAuthService for the bearer token and
 * GdmsSignatureUtil for the per-request signature - unlike DPD, a cached
 * token alone isn't enough, GDMS also needs a fresh access_token/timestamp/
 * sign query-string on every single request.
 */
@Service
@RequiredArgsConstructor
public class GdmsChannelService {

    private static final Logger log = LoggerFactory.getLogger(GdmsChannelService.class);

    // GDMS itself doesn't document a hard per-call limit for /assign, but a
    // prior manual process reportedly hit a 500-unit ceiling - batching in
    // chunks of 100 keeps every call comfortably under that with room to
    // spare, and keeps any one failure from losing an entire day's despatch
    // in one go (see runBatched below).
    static final int ASSIGN_BATCH_SIZE = 100;

    // Where the last successfully-fetched live channel list is cached (as
    // JSON), mirroring DpdShippingService's LAST_KNOWN_SERVICES_KEY pattern -
    // so the Companies.tsx dropdown still has real, previously-offered
    // channels to show when a later live lookup fails.
    private static final String LAST_KNOWN_CHANNELS_KEY = "gdms_last_known_channels";

    private final GdmsAuthService gdmsAuthService;
    private final SettingsService settingsService;
    private final GdmsSyncLogService gdmsSyncLogService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * The channels (subordinate reseller accounts) available to assign a
     * company to - powers the dropdown on Companies.tsx. Falls back to the
     * last successfully-fetched list (from Settings) when the live call
     * fails, same reasoning as DpdShippingService.listAvailableServices.
     */
    public GdmsChannelLookupResult listChannels() {
        try {
            List<GdmsChannelOption> channels = fetchChannels();
            if (!channels.isEmpty()) {
                cacheLastKnownChannels(channels);
            }
            return new GdmsChannelLookupResult(channels, true, null);
        } catch (Exception e) {
            log.warn("Live GDMS channel lookup failed: {}", e.getMessage());
            return new GdmsChannelLookupResult(loadLastKnownChannels(), false, e.getMessage());
        }
    }

    private List<GdmsChannelOption> fetchChannels() {
        // /sub/list - no body needed to list every subordinate channel BNS
        // has; GDMS's own paging defaults are generous enough for the
        // handful of reseller channels BNS actually has, so no pagination
        // loop here - revisit if that stops being true.
        JsonNode response = post("/sub/list", null);
        JsonNode list = firstArray(response);
        List<GdmsChannelOption> options = new ArrayList<>();
        for (JsonNode item : list) {
            String id = firstText(item, "subEnterpriseId", "id");
            // The doc's own Success_Response example names this field
            // "enterpriseName", not "subEnterpriseName" - the latter was a
            // reasonable-looking guess (matching the id field's naming) that
            // was never actually confirmed against a real response, and
            // meant every channel resolved to showing its own numeric ID as
            // its name instead of the reseller's actual company name.
            String name = firstText(item, "enterpriseName", "subEnterpriseName", "name", "companyName");
            if (id != null) {
                options.add(new GdmsChannelOption(id, name != null ? name : id));
            }
        }
        return options;
    }

    /**
     * Assigns every MAC in `macs` to the given channel, sending them in
     * batches of {@link #ASSIGN_BATCH_SIZE} rather than one giant call - per
     * the user's explicit request, since a prior manual process reportedly
     * hit a 500-unit limit. Returns only the MACs GDMS actually accepted, so
     * GdmsEndOfDayService only marks those as synced.
     *
     * A batch call can come back with HTTP 200/retCode 0 (the call itself
     * succeeded) while still individually rejecting some MACs within it -
     * the doc's own Success_Response example for /assign shows a
     * "data.result.errorMacList" (each with its own "mac"/"errorMsg") plus
     * "success"/"failure" counts, alongside the same top-level "retCode": 0.
     * Originally this just added the whole batch to `assigned`
     * unconditionally on any non-throwing response, so a MAC GDMS actually
     * rejected (e.g. already assigned elsewhere) would still get
     * gdmsSyncedAt set and never be retried.
     *
     * `channelName` and `macToOrderNumber` exist purely to give the GDMS sync
     * log a readable row per MAC (see GdmsSyncLogService) - `source` is the
     * free-text origin of this call ("Scheduled", "Manual", "Manual (Order
     * BNS-1234)", etc.), written verbatim onto every row this call produces.
     */
    public List<String> assignMacsToChannel(String channelId, String channelName, List<String> macs,
                                             Map<String, String> macToOrderNumber, String source) {
        if (channelId == null || channelId.isBlank()) {
            throw new ValidationException("No GDMS channel is set for this company");
        }
        List<String> assigned = new ArrayList<>();
        int failedCount = 0;
        for (int start = 0; start < macs.size(); start += ASSIGN_BATCH_SIZE) {
            List<String> batch = macs.subList(start, Math.min(start + ASSIGN_BATCH_SIZE, macs.size()));
            ObjectNode body = objectMapper.createObjectNode();
            body.put("subEnterpriseId", channelId);
            var macArray = body.putArray("macList");
            batch.forEach(macArray::add);

            JsonNode response;
            try {
                response = post("/assign", body);
            } catch (Exception e) {
                // The call itself failed (network/auth/GDMS-side error)
                // before GDMS ever got to consider individual MACs -
                // previously this batch would propagate straight up with no
                // record of the attempt at all. Every MAC in it now gets a
                // FAILURE row before the exception is rethrown, so the log
                // page still shows what was attempted even when the call
                // never got a usable response.
                for (String mac : batch) {
                    gdmsSyncLogService.recordFailure("ASSIGN", source, macToOrderNumber.get(mac), mac,
                            channelId, channelName, e.getMessage());
                }
                notifyFailure("ASSIGN", source, batch.size(), channelName);
                throw e;
            }

            List<String> acceptedInBatch = acceptedMacs(response, batch);
            JsonNode errorList = errorListOf(response);
            for (String mac : batch) {
                if (acceptedInBatch.contains(mac)) {
                    gdmsSyncLogService.recordSuccess("ASSIGN", source, macToOrderNumber.get(mac), mac, channelId, channelName);
                } else {
                    gdmsSyncLogService.recordFailure("ASSIGN", source, macToOrderNumber.get(mac), mac,
                            channelId, channelName, errorMessageFor(errorList, mac));
                }
            }
            failedCount += batch.size() - acceptedInBatch.size();
            assigned.addAll(acceptedInBatch);
        }
        // One notification per call (not per internal 100-MAC batch) - the
        // "shipped before Grandstream could assign it to our channel" case
        // Dan raised this feature for typically fails a handful of MACs at
        // once, not the whole day's despatch, so this stays a single bell
        // entry per run rather than spamming one per chunk.
        if (failedCount > 0) {
            notifyFailure("ASSIGN", source, failedCount, channelName);
        }
        return assigned;
    }

    /**
     * Recalls (via /channel/recycle) every MAC in `macs` from wherever GDMS
     * currently has it assigned - used whenever a previously-synced unit
     * moves back into stock (RMA, reverse to despatch, cancel and return).
     * Same batching/error-per-MAC/logging shape as assignMacsToChannel, but
     * /recycle's own request field is "macs" (confirmed from the doc's own
     * parameter.examples), not "macList" like /assign, and it needs no
     * subEnterpriseId - GDMS reclaims a MAC from whichever channel it's
     * actually sitting in, so there's no channelId/channelName to log here.
     * Returns the MACs GDMS actually reclaimed; callers here are best-effort
     * (see GdmsRecallService) and don't currently act on the return value.
     */
    public List<String> reclaimMacs(List<String> macs, Map<String, String> macToOrderNumber, String source) {
        List<String> recalled = new ArrayList<>();
        int failedCount = 0;
        for (int start = 0; start < macs.size(); start += ASSIGN_BATCH_SIZE) {
            List<String> batch = macs.subList(start, Math.min(start + ASSIGN_BATCH_SIZE, macs.size()));
            ObjectNode body = objectMapper.createObjectNode();
            var macArray = body.putArray("macs");
            batch.forEach(macArray::add);

            JsonNode response;
            try {
                response = post("/recycle", body);
            } catch (Exception e) {
                for (String mac : batch) {
                    gdmsSyncLogService.recordFailure("RECALL", source, macToOrderNumber.get(mac), mac,
                            null, null, e.getMessage());
                }
                notifyFailure("RECALL", source, batch.size(), null);
                throw e;
            }

            List<String> acceptedInBatch = acceptedMacs(response, batch);
            JsonNode errorList = errorListOf(response);
            for (String mac : batch) {
                if (acceptedInBatch.contains(mac)) {
                    gdmsSyncLogService.recordSuccess("RECALL", source, macToOrderNumber.get(mac), mac, null, null);
                } else {
                    gdmsSyncLogService.recordFailure("RECALL", source, macToOrderNumber.get(mac), mac,
                            null, null, errorMessageFor(errorList, mac));
                }
            }
            failedCount += batch.size() - acceptedInBatch.size();
            recalled.addAll(acceptedInBatch);
        }
        if (failedCount > 0) {
            notifyFailure("RECALL", source, failedCount, null);
        }
        return recalled;
    }

    /**
     * Creates one bell notification summarising a failed GDMS assign/recall
     * attempt, linking straight to the GDMS Log page pre-filtered to today
     * and FAILURE status - the whole point being that a failure (e.g. new
     * stock despatched before Grandstream had assigned it to our channel
     * yet) is surfaced immediately rather than only discoverable by someone
     * thinking to go check the log.
     */
    private void notifyFailure(String operation, String source, int failedCount, String channelName) {
        String today = java.time.LocalDate.now().toString();
        String verb = "ASSIGN".equals(operation) ? "assign" : "recall";
        String channelPart = channelName != null && !channelName.isBlank() ? " on " + channelName : "";
        String message = String.format("GDMS %s failed for %d device(s)%s (%s)", verb, failedCount, channelPart, source);
        String link = "/gdms-log?from=" + today + "&to=" + today + "&status=FAILURE";
        notificationService.create("GDMS_FAILURE", message, link);
    }

    /**
     * Reads data.result.errorMacList out of an /assign response and returns
     * every MAC from `batch` that ISN'T in it - i.e. the ones GDMS actually
     * accepted. Defensive: if the response doesn't have the expected shape
     * at all (a future GDMS change, or an endpoint variant not exactly
     * matching the doc's example), assumes the whole batch succeeded rather
     * than silently dropping it - `post()` already throws on a non-zero
     * retCode/non-2xx HTTP response, so reaching this point at all means
     * GDMS considered the call itself successful.
     */
    private List<String> acceptedMacs(JsonNode response, List<String> batch) {
        JsonNode data = response.has("data") ? response.get("data") : response;
        JsonNode result = data.has("result") ? data.get("result") : data;
        JsonNode errorList = result.get("errorMacList");
        if (errorList == null || !errorList.isArray() || errorList.isEmpty()) {
            return batch;
        }
        java.util.Set<String> failedMacs = new java.util.HashSet<>();
        for (JsonNode error : errorList) {
            String mac = firstText(error, "mac");
            if (mac != null) failedMacs.add(mac);
        }
        List<String> accepted = new ArrayList<>();
        for (String mac : batch) {
            if (!failedMacs.contains(mac)) {
                accepted.add(mac);
            } else {
                log.warn("GDMS rejected MAC {} during channel assignment: {}", mac,
                        errorMessageFor(errorList, mac));
            }
        }
        return accepted;
    }

    private String errorMessageFor(JsonNode errorList, String mac) {
        if (errorList == null || !errorList.isArray()) return "no error message given";
        for (JsonNode error : errorList) {
            if (mac.equals(firstText(error, "mac"))) {
                String msg = firstText(error, "errorMsg");
                return msg != null ? msg : "no error message given";
            }
        }
        return "no error message given";
    }

    /** Same data.result.errorMacList lookup acceptedMacs() does, exposed separately for the per-MAC log rows. */
    private JsonNode errorListOf(JsonNode response) {
        JsonNode data = response.has("data") ? response.get("data") : response;
        JsonNode result = data.has("result") ? data.get("result") : data;
        return result.get("errorMacList");
    }

    // --- HTTP plumbing -----------------------------------------------------

    private JsonNode post(String path, ObjectNode body) {
        String accessToken = gdmsAuthService.getAccessToken();
        long timestamp = Instant.now().toEpochMilli();
        String jsonBody = body != null ? body.toString() : null;
        String signature = GdmsSignatureUtil.calculateSignature(accessToken, timestamp,
                gdmsAuthService.clientId(), gdmsAuthService.clientSecret(), jsonBody);

        // The doc's Common Parameters table names this field "signature",
        // not "sign" - sending it as "sign" is exactly what produced GDMS's
        // "signature not exists" error (it never found a parameter by the
        // name it was actually looking for).
        String query = "access_token=" + encode(accessToken)
                + "&timestamp=" + timestamp
                + "&signature=" + signature;

        // Every Channel Management endpoint sits under a "/channel" prefix
        // per the doc's own sample request URLs (e.g. ".../channel/sub/list",
        // ".../channel/assign") - originally missing entirely, so every call
        // here was hitting a URL that didn't exist on GDMS's side at all.
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(gdmsAuthService.baseUrl() + "/channel" + path + "?" + query))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        builder = jsonBody != null
                ? builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                : builder.POST(HttpRequest.BodyPublishers.noBody());

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode parsed = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !isSuccess(parsed)) {
                log.error("GDMS {} failed - HTTP {}: {}", path, response.statusCode(), response.body());
                throw new ValidationException("GDMS " + path + " failed - " + describeError(parsed, response.statusCode()));
            }
            return parsed;
        } catch (ValidationException e) {
            throw e;
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to call GDMS " + path + ": " + e.getMessage(), e);
        }
    }

    // GDMS wraps every Channel Management response as {"retCode": 0, "msg":
    // ..., "data": ...} on success - a non-zero retCode is a business-logic
    // failure even when the HTTP status itself is 200, so both are checked.
    // The field is "retCode", confirmed from the doc's own Success_Response
    // examples for both /sub/list and /assign - originally checked "code"
    // instead, which meant this always fell through to the "field missing
    // -> assume success" default and never actually caught a business-logic
    // failure GDMS reported via a non-zero retCode with HTTP 200.
    private boolean isSuccess(JsonNode parsed) {
        if (!parsed.has("retCode")) return true;
        return parsed.path("retCode").asInt(0) == 0;
    }

    private String describeError(JsonNode parsed, int statusCode) {
        String msg = parsed != null ? parsed.path("msg").asText(null) : null;
        if (msg != null && !msg.isBlank()) {
            return "GDMS said: " + msg;
        }
        return "GDMS returned HTTP " + statusCode;
    }

    private JsonNode firstArray(JsonNode response) {
        JsonNode data = response.has("data") ? response.get("data") : response;
        if (data.isArray()) return data;
        // /sub/list's actual (paginated) response wraps the array as
        // data.result, per the doc's own Success_Response example - not
        // "list"/"rows"/"content" as originally guessed, which meant every
        // live lookup silently returned zero channels despite a successful,
        // error-free call (isSuccess() below has the same "no code field"
        // reason this went unnoticed - retCode isn't "code" either).
        for (String key : List.of("result", "list", "rows", "content")) {
            if (data.has(key) && data.get(key).isArray()) return data.get(key);
        }
        return objectMapper.createArrayNode();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                String value = node.get(field).asText(null);
                if (value != null && !value.isBlank()) return value;
            }
        }
        return null;
    }

    private void cacheLastKnownChannels(List<GdmsChannelOption> channels) {
        try {
            settingsService.set(LAST_KNOWN_CHANNELS_KEY, objectMapper.writeValueAsString(channels));
        } catch (Exception e) {
            log.warn("Failed to cache last-known GDMS channels: {}", e.getMessage());
        }
    }

    private List<GdmsChannelOption> loadLastKnownChannels() {
        String cached = settingsService.get(LAST_KNOWN_CHANNELS_KEY, "");
        if (cached.isBlank()) return List.of();
        try {
            return objectMapper.readValue(cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, GdmsChannelOption.class));
        } catch (Exception e) {
            log.warn("Failed to read cached GDMS channels: {}", e.getMessage());
            return List.of();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
