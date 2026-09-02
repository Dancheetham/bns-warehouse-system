package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.RmaLookupResult;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.repository.StockItemRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * The old process traced a MAC through StockMovement history to find the P/O
 * number, then the order date, then the invoice number, across three separate
 * screens. Picking now links every despatched StockItem straight to the OrderLine
 * it was picked against, so the whole trace collapses into one lookup - as long as
 * the unit was despatched through this system rather than inherited from history
 * predating it, in which case this correctly falls back to "not matched".
 *
 * The return window differs by whether the item is faulty - a short non-faulty
 * return window (28 days by default) vs. the much longer RTB warranty (1 year by
 * default) for faulty items. Both are configurable in Settings.
 */
@Service
@RequiredArgsConstructor
public class RmaLookupService {

    private final StockItemRepository stockItemRepository;
    private final SettingsService settingsService;

    public static final String NON_FAULTY_RETURN_DAYS_KEY = "rma_non_faulty_return_days";
    public static final String FAULTY_WARRANTY_DAYS_KEY = "rma_faulty_warranty_days";
    private static final String DEFAULT_NON_FAULTY_RETURN_DAYS = "28";
    private static final String DEFAULT_FAULTY_WARRANTY_DAYS = "365";

    public RmaLookupResult lookup(String identifier, boolean faulty) {
        int windowDays = faulty
                ? parseOrDefault(settingsService.get(FAULTY_WARRANTY_DAYS_KEY, DEFAULT_FAULTY_WARRANTY_DAYS), 365)
                : parseOrDefault(settingsService.get(NON_FAULTY_RETURN_DAYS_KEY, DEFAULT_NON_FAULTY_RETURN_DAYS), 28);

        if (identifier == null || identifier.isBlank()) {
            return new RmaLookupResult(identifier, false, false, null, null, null, null, null, null, null, null, null, windowDays);
        }
        String trimmed = identifier.trim();

        Optional<StockItem> byMac = stockItemRepository.findByMacAddressIgnoreCase(trimmed);
        Optional<StockItem> bySerial = stockItemRepository.findBySerialNumberIgnoreCase(trimmed);
        StockItem item = byMac.orElse(bySerial.orElse(null));

        if (item == null) {
            return new RmaLookupResult(identifier, false, false, null, null, null, null, null, null, null, null, null, windowDays);
        }

        OrderLine orderLine = item.getOrderLine();
        if (orderLine == null) {
            return new RmaLookupResult(identifier, true, false,
                    item.getProduct().getId(), item.getProduct().getSku(), item.getProduct().getName(),
                    null, null, null, null, null, null, windowDays);
        }

        Order order = orderLine.getOrder();
        LocalDate windowExpires = order.getOrderDate().toLocalDate().plusDays(windowDays);
        boolean windowValid = !windowExpires.isBefore(LocalDate.now());

        return new RmaLookupResult(identifier, true, true,
                item.getProduct().getId(), item.getProduct().getSku(), item.getProduct().getName(),
                order.getId(), order.getOrderNumber(), order.getOrderDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                orderLine.getUnitPrice(), windowExpires, windowValid, windowDays);
    }

    private int parseOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
