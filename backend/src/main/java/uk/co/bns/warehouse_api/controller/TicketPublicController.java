package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.PublicTicketEntryRequest;
import uk.co.bns.warehouse_api.dto.PublicTicketRequest;
import uk.co.bns.warehouse_api.dto.TicketEntryView;
import uk.co.bns.warehouse_api.dto.TicketView;
import uk.co.bns.warehouse_api.entity.TicketEntry;
import uk.co.bns.warehouse_api.service.TicketService;

/**
 * For a third-party call-transcription/CRM-import service to open a ticket -
 * or add to one it already opened - straight from a finished call. All
 * endpoints under /api/public require a valid X-API-Key header (see
 * ApiKeyInterceptor), same as PublicStockController - keys are managed from
 * the internal "API Access" screen.
 */
@RestController
@RequestMapping("/api/public/tickets")
@RequiredArgsConstructor
public class TicketPublicController {

    private final TicketService ticketService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketView create(@Valid @RequestBody PublicTicketRequest request) {
        return ticketService.toView(ticketService.createFromPublicApi(request));
    }

    @PostMapping("/{ticketNumber}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketEntryView addEntry(@PathVariable String ticketNumber, @Valid @RequestBody PublicTicketEntryRequest request) {
        TicketEntry entry = ticketService.addEntryByTicketNumber(ticketNumber, request.note(), request.author());
        return new TicketEntryView(entry.getId(), entry.getAuthor(), entry.getNote(), entry.getCreatedAt());
    }
}
