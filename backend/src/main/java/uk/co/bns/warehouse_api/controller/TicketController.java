package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.TicketEntryRequest;
import uk.co.bns.warehouse_api.dto.TicketEntryView;
import uk.co.bns.warehouse_api.dto.TicketRequest;
import uk.co.bns.warehouse_api.dto.TicketSummaryView;
import uk.co.bns.warehouse_api.dto.TicketView;
import uk.co.bns.warehouse_api.entity.TicketEntry;
import uk.co.bns.warehouse_api.service.TicketService;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public List<TicketSummaryView> getAll() {
        return ticketService.findAll().stream().map(ticketService::toSummaryView).toList();
    }

    @GetMapping("/{id}")
    public TicketView getOne(@PathVariable Long id) {
        return ticketService.toView(ticketService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketView create(@Valid @RequestBody TicketRequest request) {
        return ticketService.toView(ticketService.create(request));
    }

    @PutMapping("/{id}")
    public TicketView update(@PathVariable Long id, @Valid @RequestBody TicketRequest request) {
        return ticketService.toView(ticketService.update(id, request));
    }

    // Author is left to the logged-in user (authentication.getName(), their
    // full name - same pattern as PickingController/DespatchController) unless
    // the request body explicitly names someone else.
    @PostMapping("/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketEntryView addEntry(@PathVariable Long id, @Valid @RequestBody TicketEntryRequest request,
                                     Authentication authentication) {
        String defaultAuthor = authentication != null ? authentication.getName() : "Unknown";
        TicketEntry entry = ticketService.addEntry(id, request, defaultAuthor);
        return new TicketEntryView(entry.getId(), entry.getAuthor(), entry.getNote(), entry.getCreatedAt());
    }

    @GetMapping("/by-company/{companyId}")
    public List<TicketSummaryView> byCompany(@PathVariable Long companyId) {
        return ticketService.findByCompany(companyId).stream().map(ticketService::toSummaryView).toList();
    }

    @GetMapping("/by-order/{orderId}")
    public List<TicketSummaryView> byOrder(@PathVariable Long orderId) {
        return ticketService.findByOrder(orderId).stream().map(ticketService::toSummaryView).toList();
    }
}
