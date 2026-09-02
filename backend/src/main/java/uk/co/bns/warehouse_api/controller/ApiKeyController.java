package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.ApiKeyCreatedResponse;
import uk.co.bns.warehouse_api.dto.ApiKeySummary;
import uk.co.bns.warehouse_api.dto.CreateApiKeyRequest;
import uk.co.bns.warehouse_api.service.ApiKeyService;

import java.util.List;

@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public List<ApiKeySummary> getAll() {
        return apiKeyService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreatedResponse create(@Valid @RequestBody CreateApiKeyRequest request) {
        return apiKeyService.create(request.label());
    }

    @PostMapping("/{id}/deactivate")
    public void deactivate(@PathVariable Long id) {
        apiKeyService.deactivate(id);
    }
}
