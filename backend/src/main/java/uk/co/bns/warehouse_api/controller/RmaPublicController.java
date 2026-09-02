package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.RmaLookupResult;
import uk.co.bns.warehouse_api.dto.RmaSubmissionRequest;
import uk.co.bns.warehouse_api.dto.RmaSubmissionResult;
import uk.co.bns.warehouse_api.entity.RmaRequest;
import uk.co.bns.warehouse_api.service.RmaLookupService;
import uk.co.bns.warehouse_api.service.RmaService;

/**
 * Genuinely public, unauthenticated endpoints - the RMA form and its live lookup.
 * Nothing else in this app is authenticated yet either, but worth flagging: once
 * this is ever exposed past the LAN, this specific controller is the one that
 * needs abuse protection (rate limiting, a captcha) since it's designed to accept
 * requests from anyone with the link, not just staff.
 */
@RestController
@RequestMapping("/api/rma-requests")
@RequiredArgsConstructor
public class RmaPublicController {

    private final RmaService rmaService;
    private final RmaLookupService lookupService;

    @GetMapping("/lookup")
    public RmaLookupResult lookup(@RequestParam String identifier, @RequestParam(defaultValue = "false") boolean faulty) {
        return lookupService.lookup(identifier, faulty);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RmaSubmissionResult submit(@Valid @RequestBody RmaSubmissionRequest request) {
        RmaRequest rma = rmaService.submit(request);
        return new RmaSubmissionResult(rma.getPublicReference());
    }
}
