package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.co.bns.warehouse_api.entity.CollectionCourierOption;
import uk.co.bns.warehouse_api.service.CollectionCourierOptionService;

import java.util.List;

/**
 * Active-only Collection courier list for the order screen's Collection
 * dropdown (OrderEdit.tsx) - separate from the admin CRUD under
 * SettingsController (which also returns inactive rows, for the Settings >
 * Couriers admin list), matching how GDMS channels are similarly exposed to
 * order/company screens outside of Settings itself.
 */
@RestController
@RequestMapping("/api/collection-courier-options")
@RequiredArgsConstructor
public class CollectionCourierOptionController {

    private final CollectionCourierOptionService service;

    @GetMapping
    public List<CollectionCourierOption> active() {
        return service.findActive();
    }
}
