package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.enums.StockItemStatus;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {
    Optional<StockItem> findByMacAddress(String macAddress);
    Optional<StockItem> findBySerialNumber(String serialNumber);
    Optional<StockItem> findByMacAddressIgnoreCase(String macAddress);
    Optional<StockItem> findBySerialNumberIgnoreCase(String serialNumber);
    List<StockItem> findByBatchCodeIgnoreCase(String batchCode);
    List<StockItem> findByProduct_Id(Long productId);
    List<StockItem> findByLocation_Id(Long locationId);
    List<StockItem> findByProduct_IdAndLocation_IdAndStatusOrderByIdAsc(Long productId, Long locationId, StockItemStatus status);
    long countByProduct_IdAndStatus(Long productId, StockItemStatus status);

    Optional<StockItem> findByMacAddressIgnoreCaseAndStatus(String macAddress, StockItemStatus status);
    Optional<StockItem> findBySerialNumberIgnoreCaseAndStatus(String serialNumber, StockItemStatus status);
    List<StockItem> findByBatchCodeIgnoreCaseAndStatusOrderByIdAsc(String batchCode, StockItemStatus status);
    List<StockItem> findByProduct_IdAndStatusOrderByIdAsc(Long productId, StockItemStatus status);
    List<StockItem> findByOrderLine_Id(Long orderLineId);
    List<StockItem> findByOrderLine_Order_Id(Long orderId);
    List<StockItem> findByOrderLine_Order_IdAndStatus(Long orderId, StockItemStatus status);
    List<StockItem> findByCarton_Id(Long cartonId);
}
