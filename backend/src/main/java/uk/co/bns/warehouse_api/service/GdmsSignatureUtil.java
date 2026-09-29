package uk.co.bns.warehouse_api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * GDMS's request-signing algorithm, as documented under "Signature Method"
 * on https://doc.grandstream.dev/GDMS-API/EN/ - materially different from
 * DPD's plain bearer token, GDMS needs a fresh HMAC-style signature
 * calculated on every request, not just a cached auth header.
 *
 * For a JSON-body request (every Channel Management call BNS uses is a POST
 * with a JSON body, never multipart/form - the form-submission variant of
 * this algorithm documented alongside this one, for file/image uploads, is
 * deliberately not implemented here):
 * <ol>
 *   <li>Collect the common parameters - {@code access_token}, {@code
 *       timestamp}, {@code client_id}, {@code client_secret} - sort them
 *       ascending (ties broken by case - uppercase sorts before lowercase,
 *       which is exactly what {@link String#compareTo} already does), and
 *       join as {@code key=value} pairs separated by {@code &}.</li>
 *   <li>If there's a JSON request body, compute {@code sha256(body)} (the
 *       exact JSON string sent, lowercase hex) as an extra component.</li>
 *   <li>Final signature = {@code sha256("&" + paramsJoined + "&" +
 *       sha256(body) + "&")} when there's a body, or {@code
 *       sha256("&" + paramsJoined + "&")} when there isn't - both lowercase
 *       hex.</li>
 * </ol>
 *
 * client_id/client_secret go INTO this calculation but are never sent as
 * literal request parameters on an ordinary (already-authenticated) API
 * call - only {@code access_token}, {@code timestamp} and the resulting
 * {@code signature} are actually sent. They're only sent as real request
 * params on the {@code /oauth/token} call itself, which doesn't use this
 * signature scheme at all (see GdmsAuthService).
 */
public final class GdmsSignatureUtil {

    private GdmsSignatureUtil() {}

    /**
     * Builds the signature for an already-authenticated JSON-body Channel
     * Management call, whose signed params are always exactly the four
     * common ones. For a request with a different param set (the token
     * endpoints - see {@link #calculateSignature(SortedMap, String)}), use
     * the more general overload instead.
     *
     * @param accessToken the current access_token
     * @param timestampMillis milliseconds since epoch, generated fresh per request
     * @param clientId GDMS developer client_id (Settings > GDMS)
     * @param clientSecret GDMS developer client_secret (Settings > GDMS)
     * @param jsonBody the exact JSON string being sent as the request body, or null/blank if there isn't one
     */
    public static String calculateSignature(String accessToken, long timestampMillis, String clientId,
                                             String clientSecret, String jsonBody) {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("access_token", accessToken);
        params.put("timestamp", String.valueOf(timestampMillis));
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        return calculateSignature(params, jsonBody);
    }

    /**
     * The general form of the algorithm: sign whatever params are actually
     * given (already sorted ascending by the caller via the SortedMap),
     * joined as key=value pairs separated by "&", wrapped as
     * "&" + paramsJoined + "&" plus sha256(body) + "&" when there's a body.
     *
     * Needed because the "Getting Token"/"Refreshing Token" endpoints sign a
     * different param set than every other call: per the doc's Common
     * Parameters table, access_token/timestamp/signature are "not required
     * to be carried except for Getting Token interface and Refreshing Token
     * interface" - i.e. those two endpoints are the ones that DO need
     * timestamp+signature (there's no access_token yet to include), signed
     * together with their own request params (username/password/grant_type/
     * client_id/client_secret for Getting Token; refresh_token/grant_type/
     * client_id/client_secret for Refreshing Token) rather than the fixed
     * four-param set every other endpoint uses.
     */
    public static String calculateSignature(SortedMap<String, String> params, String jsonBody) {
        StringBuilder paramsJoined = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (paramsJoined.length() > 0) paramsJoined.append("&");
            paramsJoined.append(entry.getKey()).append("=").append(entry.getValue());
        }

        StringBuilder toSign = new StringBuilder("&").append(paramsJoined).append("&");
        if (jsonBody != null && !jsonBody.isBlank()) {
            toSign.append(sha256Hex(jsonBody)).append("&");
        }
        return sha256Hex(toSign.toString());
    }

    /**
     * The password encoding required by {@code GET /oauth/token}: MD5 the
     * plaintext password to a 32-char lowercase hex string, then SHA-256
     * *that string* (not the raw password bytes) to another lowercase hex
     * string. Easy to get subtly wrong (hex-of-bytes vs hex-of-string,
     * upper vs lowercase) - kept as one clearly-named method rather than
     * inlined at the call site so it only has to be got right once.
     */
    public static String encodePasswordForToken(String plainPassword) {
        return sha256Hex(md5Hex(plainPassword));
    }

    public static String sha256Hex(String value) {
        return hex(digest("SHA-256", value));
    }

    private static String md5Hex(String value) {
        return hex(digest("MD5", value));
    }

    private static byte[] digest(String algorithm, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " not available", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
