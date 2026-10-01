package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.CourierServiceOption;

import java.util.List;
import java.util.Optional;

public interface CourierServiceOptionRepository extends JpaRepository<CourierServiceOption, Long> {
    List<CourierServiceOption> findByCourierOrderByCodeAsc(String courier);
    Optional<CourierServiceOption> findByCourierAndCode(String courier, String code);
}
