package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.ExpectedCarton;

import java.util.List;
import java.util.Optional;

public interface ExpectedCartonRepository extends JpaRepository<ExpectedCarton, Long> {
    Optional<ExpectedCarton> findByBatchCode(String batchCode);
    Optional<ExpectedCarton> findByBatchCodeIgnoreCase(String batchCode);
    List<ExpectedCarton> findByPurchaseOrderLine_PurchaseOrder_Id(Long purchaseOrderId);
}
