package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.GoodsInSession;
import uk.co.bns.warehouse_api.enums.GoodsInSessionStatus;

import java.util.List;

public interface GoodsInSessionRepository extends JpaRepository<GoodsInSession, Long> {
    List<GoodsInSession> findByStatusOrderByStartedAtAsc(GoodsInSessionStatus status);
}
