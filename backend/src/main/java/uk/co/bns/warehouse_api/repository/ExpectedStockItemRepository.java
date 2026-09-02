package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.ExpectedStockItem;

import java.util.Optional;

public interface ExpectedStockItemRepository extends JpaRepository<ExpectedStockItem, Long> {
    Optional<ExpectedStockItem> findByMacAddress(String macAddress);
    Optional<ExpectedStockItem> findBySerialNumber(String serialNumber);
}
