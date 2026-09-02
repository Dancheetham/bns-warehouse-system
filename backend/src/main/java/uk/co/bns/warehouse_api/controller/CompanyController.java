package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.CompanyRequest;
import uk.co.bns.warehouse_api.dto.CompanyView;
import uk.co.bns.warehouse_api.service.CompanyService;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public List<CompanyView> getAll() {
        return companyService.findAll().stream().map(companyService::toView).toList();
    }

    @GetMapping("/{id}")
    public CompanyView getOne(@PathVariable Long id) {
        return companyService.toView(companyService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyView create(@Valid @RequestBody CompanyRequest request) {
        return companyService.toView(companyService.create(request));
    }

    @PutMapping("/{id}")
    public CompanyView update(@PathVariable Long id, @Valid @RequestBody CompanyRequest request) {
        return companyService.toView(companyService.update(id, request));
    }
}
