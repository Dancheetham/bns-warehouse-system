package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.StockMovement;
import uk.co.bns.warehouse_api.enums.MovementType;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByStockItem_IdOrderByCreatedAtAsc(Long stockItemId);

    // GdmsEndOfDayService - every DESPATCH movement in a given time window,
    // used to find "what left the building today" by the movement's own
    // createdAt rather than Order.despatchedAt (see GdmsEndOfDayService for
    // why - the per-item movement timestamp is the reliable one).
    List<StockMovement> findByMovementTypeAndCreatedAtBetween(MovementType movementType, LocalDateTime from, LocalDateTime to);

    // Per-order manual trigger - every DESPATCH movement for items on a
    // given order, regardless of when it happened.
    List<StockMovement> findByMovementTypeAndStockItem_OrderLine_Order_Id(MovementType movementType, Long orderId);
}
