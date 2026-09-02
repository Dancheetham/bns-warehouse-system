package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.StockTraceEventDto;
import uk.co.bns.warehouse_api.dto.StockTraceResult;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.entity.StockMovement;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.StockItemRepository;
import uk.co.bns.warehouse_api.repository.StockMovementRepository;

import java.util.List;

/**
 * The "search box that accepts anything" feature - type a MAC, serial, or a
 * batch/carton code and land straight on that item's (or that carton's) full
 * history. This is the feature the transcript identified as the core value
 * proposition of the whole system. All lookups are case-insensitive, since
 * stock data is normalised to uppercase on import but scanners, spreadsheets
 * and people typing by hand won't always match that exactly.
 */
@Service
@RequiredArgsConstructor
public class StockTraceService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;

    public StockTraceResult traceByMac(String macAddress) {
        StockItem item = stockItemRepository.findByMacAddressIgnoreCase(macAddress)
                .orElseThrow(() -> new NotFoundException("No stock item found for MAC address " + macAddress));
        return buildResult("MAC", macAddress, item);
    }

    public StockTraceResult traceBySerial(String serialNumber) {
        StockItem item = stockItemRepository.findBySerialNumberIgnoreCase(serialNumber)
                .orElseThrow(() -> new NotFoundException("No stock item found for serial number " + serialNumber));
        return buildResult("SERIAL", serialNumber, item);
    }

    public List<StockTraceResult> traceByBatch(String batchCode) {
        List<StockItem> items = stockItemRepository.findByBatchCodeIgnoreCase(batchCode);
        if (items.isEmpty()) {
            throw new NotFoundException("No stock items found for batch/carton code " + batchCode);
        }
        return items.stream()
                .map(item -> buildResult(
                        item.getMacAddress() != null ? "MAC" : "SERIAL",
                        item.getMacAddress() != null ? item.getMacAddress() : item.getSerialNumber(),
                        item
                ))
                .toList();
    }

    private StockTraceResult buildResult(String identifierType, String identifier, StockItem item) {
        List<StockMovement> movements = stockMovementRepository.findByStockItem_IdOrderByCreatedAtAsc(item.getId());

        List<StockTraceEventDto> timeline = movements.stream()
                .map(m -> new StockTraceEventDto(
                        m.getCreatedAt(),
                        m.getMovementType().name(),
                        m.getFromLocation() != null ? m.getFromLocation().getCode() : null,
                        m.getToLocation() != null ? m.getToLocation().getCode() : null,
                        m.getReference(),
                        m.getNotes(),
                        m.getCreatedBy()
                ))
                .toList();

        return new StockTraceResult(
                identifierType,
                identifier,
                item.getProduct().getSku(),
                item.getProduct().getName(),
                item.getStatus().name(),
                item.getLocation() != null ? item.getLocation().getCode() : null,
                timeline
        );
    }
}
