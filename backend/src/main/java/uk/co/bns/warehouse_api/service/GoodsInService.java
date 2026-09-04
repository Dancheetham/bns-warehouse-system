package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.ScanCartonRequest;
import uk.co.bns.warehouse_api.dto.ScanCartonResult;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.CartonStatus;
import uk.co.bns.warehouse_api.enums.GoodsInSessionStatus;
import uk.co.bns.warehouse_api.enums.MovementType;
import uk.co.bns.warehouse_api.enums.POStatus;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implements the "Carton Goods In" workflow as described for the existing system:
 *
 *  1. Start a session against a PO + destination bin.
 *  2. Scan cartons one at a time.
 *       - Carton already in THIS session       -> silently ignored (no error, keeps the scan flow going)
 *       - Carton already RECEIVED previously    -> rejected with an error (data integrity issue)
 *       - Otherwise                             -> added to the session
 *  3. Save the session, which is when everything actually becomes permanent:
 *       - Real StockItem rows are created from the ExpectedStockItems in each scanned carton
 *       - Inventory quantities increase
 *       - StockMovement (RECEIPT) records are written
 *       - ExpectedCarton status flips to RECEIVED
 *
 * Nothing touches real inventory until Save is called - this mirrors the "part book in,
 * save it, come back later" behaviour of the current system.
 */
@Service
@RequiredArgsConstructor
public class GoodsInService {

    private final GoodsInSessionRepository sessionRepository;
    private final GoodsInSessionCartonRepository sessionCartonRepository;
    private final ExpectedCartonRepository expectedCartonRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final LocationRepository locationRepository;
    private final StockItemRepository stockItemRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    public GoodsInSession startSession(Long purchaseOrderId, Long locationId, String startedBy) {        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new NotFoundException("Purchase order " + purchaseOrderId + " not found"));
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location " + locationId + " not found"));

        GoodsInSession session = new GoodsInSession();
        session.setPurchaseOrder(po);
        session.setLocation(location);
        session.setStartedBy(startedBy);
        return sessionRepository.save(session);
    }

    public GoodsInSession getSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Goods-in session " + sessionId + " not found"));
    }

    /**
     * Sessions left OPEN (started but not yet saved) - lets the handheld resume one
     * after a refresh, dropped connection, or the app being backgrounded, rather
     * than losing track of an in-progress booking-in.
     */
    public List<GoodsInSession> getOpenSessions() {
        return sessionRepository.findByStatusOrderByStartedAtAsc(GoodsInSessionStatus.OPEN);
    }

    @Transactional
    public ScanCartonResult scanCarton(Long sessionId, ScanCartonRequest request) {
        GoodsInSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Goods-in session " + sessionId + " not found"));

        if (session.getStatus() == GoodsInSessionStatus.SAVED) {
            throw new ValidationException("This goods-in session has already been saved");
        }

        Optional<ExpectedCarton> cartonOpt = expectedCartonRepository.findByBatchCodeIgnoreCase(request.batchCode());
        if (cartonOpt.isEmpty()) {
            return new ScanCartonResult("NOT_FOUND", "Carton " + request.batchCode() + " was not found on any imported shipment", null, null, null);
        }

        ExpectedCarton carton = cartonOpt.get();

        if (!carton.getPurchaseOrderLine().getPurchaseOrder().getId().equals(session.getPurchaseOrder().getId())) {
            // Wrong PO entirely - this is a genuine mistake (grabbed the wrong carton),
            // not a benign duplicate, so it's reported clearly rather than silently ignored.
            return new ScanCartonResult(
                    "WRONG_PURCHASE_ORDER",
                    "Carton " + request.batchCode() + " belongs to PO "
                            + carton.getPurchaseOrderLine().getPurchaseOrder().getPoNumber()
                            + ", not this session's PO (" + session.getPurchaseOrder().getPoNumber() + ") - not booked in",
                    carton.getPurchaseOrderLine().getProduct().getSku(),
                    carton.getPurchaseOrderLine().getProduct().getName(),
                    carton.getItems().size()
            );
        }

        if (carton.getStatus() == CartonStatus.RECEIVED) {
            // Already permanently received in a previous, saved session - this IS an error
            return new ScanCartonResult(
                    "ALREADY_RECEIVED",
                    "Carton " + request.batchCode() + " has already been received on "
                            + carton.getReceivedAt() + " into " + (carton.getPurchaseOrderLine() != null ? "the warehouse" : "stock")
                            + (carton.getReceivedBy() != null ? " by " + carton.getReceivedBy() : ""),
                    carton.getPurchaseOrderLine().getProduct().getSku(),
                    carton.getPurchaseOrderLine().getProduct().getName(),
                    carton.getItems().size()
            );
        }

        Optional<GoodsInSessionCarton> existingInSession =
                sessionCartonRepository.findBySession_IdAndExpectedCarton_Id(sessionId, carton.getId());

        if (existingInSession.isPresent()) {
            // Scanned twice in the same session - ignore silently, keep the flow going
            return new ScanCartonResult(
                    "ALREADY_IN_SESSION",
                    null,
                    carton.getPurchaseOrderLine().getProduct().getSku(),
                    carton.getPurchaseOrderLine().getProduct().getName(),
                    carton.getItems().size()
            );
        }

        GoodsInSessionCarton sessionCarton = new GoodsInSessionCarton();
        sessionCarton.setSession(session);
        sessionCarton.setExpectedCarton(carton);
        sessionCartonRepository.save(sessionCarton);

        return new ScanCartonResult(
                "ADDED",
                "Carton " + request.batchCode() + " added",
                carton.getPurchaseOrderLine().getProduct().getSku(),
                carton.getPurchaseOrderLine().getProduct().getName(),
                carton.getItems().size()
        );
    }

    @Transactional
    public GoodsInSession saveSession(Long sessionId, String savedBy) {
        GoodsInSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Goods-in session " + sessionId + " not found"));

        if (session.getStatus() == GoodsInSessionStatus.SAVED) {
            throw new ValidationException("This goods-in session has already been saved");
        }

        if (session.getScannedCartons().isEmpty()) {
            throw new ValidationException("Cannot save an empty goods-in session - scan at least one carton first");
        }

        LocalDateTime now = LocalDateTime.now();

        for (GoodsInSessionCarton sc : session.getScannedCartons()) {
            ExpectedCarton carton = sc.getExpectedCarton();
            Product product = carton.getPurchaseOrderLine().getProduct();

            for (ExpectedStockItem expectedItem : carton.getItems()) {
                StockItem stockItem = new StockItem();
                stockItem.setProduct(product);
                stockItem.setMacAddress(expectedItem.getMacAddress());
                stockItem.setSerialNumber(expectedItem.getSerialNumber());
                stockItem.setWifiMacAddress(expectedItem.getWifiMacAddress());
                stockItem.setDefaultPassword(expectedItem.getDefaultPassword());
                stockItem.setBatchCode(carton.getBatchCode());
                stockItem.setLocation(session.getLocation());
                stockItem.setStatus(StockItemStatus.AVAILABLE);
                stockItem.setPurchaseOrderLine(carton.getPurchaseOrderLine());
                stockItem.setReceivedAt(now);
                stockItemRepository.save(stockItem);

                expectedItem.setReceived(true);

                StockMovement movement = new StockMovement();
                movement.setStockItem(stockItem);
                movement.setProduct(product);
                movement.setToLocation(session.getLocation());
                movement.setMovementType(MovementType.RECEIPT);
                movement.setQuantity(1);
                movement.setReference(session.getPurchaseOrder().getPoNumber());
                movement.setNotes("Carton " + carton.getBatchCode());
                movement.setCreatedBy(savedBy);
                stockMovementRepository.save(movement);
            }

            // Update bulk inventory total for this product/location
            Inventory inventory = inventoryRepository
                    .findByProduct_IdAndLocation_Id(product.getId(), session.getLocation().getId())
                    .orElseGet(() -> {
                        Inventory inv = new Inventory();
                        inv.setProduct(product);
                        inv.setLocation(session.getLocation());
                        inv.setQuantity(0);
                        return inv;
                    });
            inventory.setQuantity(inventory.getQuantity() + carton.getItems().size());
            inventoryRepository.save(inventory);

            carton.setStatus(CartonStatus.RECEIVED);
            carton.setReceivedAt(now);
            carton.setReceivedBy(savedBy);
            expectedCartonRepository.save(carton);
        }

        session.setStatus(GoodsInSessionStatus.SAVED);
        session.setSavedBy(savedBy);
        session.setSavedAt(now);
        sessionRepository.save(session);

        updatePurchaseOrderStatus(session.getPurchaseOrder());

        return session;
    }

    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<ExpectedCarton> allCartons = expectedCartonRepository.findByPurchaseOrderLine_PurchaseOrder_Id(po.getId());
        boolean allReceived = allCartons.stream().allMatch(c -> c.getStatus() == CartonStatus.RECEIVED);
        boolean anyReceived = allCartons.stream().anyMatch(c -> c.getStatus() == CartonStatus.RECEIVED);

        if (allReceived) {
            po.setStatus(POStatus.RECEIVED);
        } else if (anyReceived) {
            po.setStatus(POStatus.PART_RECEIVED);
        }
        purchaseOrderRepository.save(po);
    }
}
