package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Shipment;

import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    // Delivery History detail's "Previous Shipments" section - every
    // shipment on this order that's since been superseded by a later one.
    // The order's own dpd* fields still carry whichever shipment is
    // current, so this list is deliberately just the earlier ones.
    List<Shipment> findByOrder_IdOrderByCreatedAtAsc(Long orderId);
}
