package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.PurchaseOrderLineRequest;
import uk.co.bns.warehouse_api.dto.PurchaseOrderRequest;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.POStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.ProductRepository;
import uk.co.bns.warehouse_api.repository.PurchaseOrderRepository;
import uk.co.bns.warehouse_api.repository.SupplierRepository;

import java.time.Year;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public List<PurchaseOrder> findAll() {
        return purchaseOrderRepository.findAll();
    }

    public PurchaseOrder findById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Purchase order " + id + " not found"));
    }

    @Transactional
    public PurchaseOrder create(PurchaseOrderRequest request) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new NotFoundException("Supplier " + request.supplierId() + " not found"));

        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(generatePoNumber());
        po.setSupplier(supplier);
        po.setExpectedDate(request.expectedDate());
        po.setStatus(POStatus.AWAITING_STOCK);

        for (PurchaseOrderLineRequest lineReq : request.lines()) {
            Product product = productRepository.findById(lineReq.productId())
                    .orElseThrow(() -> new NotFoundException("Product " + lineReq.productId() + " not found"));

            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setPurchaseOrder(po);
            line.setProduct(product);
            line.setQuantityOrdered(lineReq.quantityOrdered());
            line.setUnitCost(lineReq.unitCost());
            line.setNotes(lineReq.notes());
            po.getLines().add(line);
        }

        return purchaseOrderRepository.save(po);
    }

    // Generates e.g. PO20260812-0001 (date-based, sequential-looking, matches the
    // "PO20191150"-style format mentioned for the existing system)
    private String generatePoNumber() {
        long countToday = purchaseOrderRepository.count() + 1;
        String datePart = java.time.LocalDate.now().toString().replace("-", "");
        return "PO" + datePart + String.format("%04d", countToday);
    }
}
