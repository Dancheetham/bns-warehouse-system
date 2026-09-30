package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.CollectionCourierOption;

import java.util.List;

public interface CollectionCourierOptionRepository extends JpaRepository<CollectionCourierOption, Long> {
    List<CollectionCourierOption> findAllByOrderBySortOrderAscNameAsc();
    List<CollectionCourierOption> findByActiveTrueOrderBySortOrderAscNameAsc();
}
