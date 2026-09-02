package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.ApiKeyCreatedResponse;
import uk.co.bns.warehouse_api.dto.ApiKeySummary;
import uk.co.bns.warehouse_api.entity.ApiKey;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.ApiKeyRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Keys are shown in full exactly once, at creation. Only a SHA-256 hash is ever
 * stored, so even a database leak doesn't expose usable keys - the same approach
 * used for password storage, applied here to API credentials.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    public ApiKeyCreatedResponse create(String label) {
        String rawKey = generateRawKey();

        ApiKey apiKey = new ApiKey();
        apiKey.setLabel(label);
        apiKey.setKeyHash(hash(rawKey));
        apiKeyRepository.save(apiKey);

        return new ApiKeyCreatedResponse(apiKey.getId(), apiKey.getLabel(), rawKey, apiKey.getCreatedAt());
    }

    public List<ApiKeySummary> listAll() {
        return apiKeyRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(k -> new ApiKeySummary(k.getId(), k.getLabel(), k.getActive(), k.getCreatedAt(), k.getLastUsedAt()))
                .toList();
    }

    public void deactivate(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("API key " + id + " not found"));
        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);
    }

    /**
     * Validates a raw key presented by a caller. Returns true (and records last-used)
     * if it matches an active key.
     */
    public boolean validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return false;
        Optional<ApiKey> found = apiKeyRepository.findByKeyHash(hash(rawKey));
        if (found.isEmpty() || !Boolean.TRUE.equals(found.get().getActive())) {
            return false;
        }
        ApiKey apiKey = found.get();
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(apiKey);
        return true;
    }

    private String generateRawKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "bns_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes());
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
