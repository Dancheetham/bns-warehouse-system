package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.OrderLine;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {
}
