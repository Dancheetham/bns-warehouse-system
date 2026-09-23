package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.ContactRequest;
import uk.co.bns.warehouse_api.dto.ContactView;
import uk.co.bns.warehouse_api.service.ContactService;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    public List<ContactView> getAll() {
        return contactService.findAll().stream().map(contactService::toView).toList();
    }

    @GetMapping("/{id}")
    public ContactView getOne(@PathVariable Long id) {
        return contactService.toView(contactService.findById(id));
    }

    @GetMapping("/by-company/{companyId}")
    public List<ContactView> byCompany(@PathVariable Long companyId) {
        return contactService.findByCompany(companyId).stream().map(contactService::toView).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactView create(@Valid @RequestBody ContactRequest request) {
        return contactService.toView(contactService.create(request));
    }

    @PutMapping("/{id}")
    public ContactView update(@PathVariable Long id, @Valid @RequestBody ContactRequest request) {
        return contactService.toView(contactService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        contactService.delete(id);
    }
}
