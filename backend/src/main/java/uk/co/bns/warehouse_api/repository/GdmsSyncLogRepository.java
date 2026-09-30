package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.GdmsSyncLog;

import java.util.List;

public interface GdmsSyncLogRepository extends JpaRepository<GdmsSyncLog, Long> {
    List<GdmsSyncLog> findAllByOrderByAttemptedAtDesc();
}
