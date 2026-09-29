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
            String name = firstText(item, "subEnterpriseName", "name", "companyName");
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
     * hit a 500-unit limit. Returns the MACs that were actually accepted
     * (each batch either succeeds as a whole or throws) so
     * GdmsEndOfDayService only marks those as synced.
     */
    public List<String> assignMacsToChannel(String channelId, List<String> macs) {
        if (channelId == null || channelId.isBlank()) {
            throw new ValidationException("No GDMS channel is set for this company");
        }
        List<String> assigned = new ArrayList<>();
        for (int start = 0; start < macs.size(); start += ASSIGN_BATCH_SIZE) {
            List<String> batch = macs.subList(start, Math.min(start + ASSIGN_BATCH_SIZE, macs.size()));
            ObjectNode body = objectMapper.createObjectNode();
            body.put("subEnterpriseId", channelId);
            var macArray = body.putArray("macList");
            batch.forEach(macArray::add);
            post("/assign", body);
            assigned.addAll(batch);
        }
        return assigned;
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

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(gdmsAuthService.baseUrl() + path + "?" + query))
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

    // GDMS wraps every Channel Management response as {"code": 0, "msg": ...,
    // "data": ...} on success - a non-zero code is a business-logic failure
    // even when the HTTP status itself is 200, so both are checked.
    private boolean isSuccess(JsonNode parsed) {
        if (!parsed.has("code")) return true;
        return parsed.path("code").asInt(0) == 0;
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
        // Some list endpoints wrap the array again under e.g. "list"/"rows".
        for (String key : List.of("list", "rows", "content")) {
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
