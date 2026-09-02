package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.CartonLine;

import java.util.List;

public interface CartonLineRepository extends JpaRepository<CartonLine, Long> {
    List<CartonLine> findByOrderLine_Id(Long orderLineId);
    List<CartonLine> findByOrderLine_Order_Id(Long orderId);
    List<CartonLine> findByCarton_Id(Long cartonId);
    boolean existsByOrderLine_Id(Long orderLineId);
}
