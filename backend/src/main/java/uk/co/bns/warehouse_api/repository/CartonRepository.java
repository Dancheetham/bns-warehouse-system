package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Carton;

import java.util.List;

public interface CartonRepository extends JpaRepository<Carton, Long> {
    List<Carton> findByOrder_IdOrderByCartonNumberAsc(Long orderId);
    int countByOrder_Id(Long orderId);
}
