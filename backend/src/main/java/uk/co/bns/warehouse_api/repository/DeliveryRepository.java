package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Delivery;

import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    boolean existsByDeliveryNumber(String deliveryNumber);

    List<Delivery> findByOrder_IdOrderByDespatchedAtAsc(Long orderId);

    List<Delivery> findAllByOrderByDespatchedAtDesc();

    void deleteByOrder_Id(Long orderId);
}
