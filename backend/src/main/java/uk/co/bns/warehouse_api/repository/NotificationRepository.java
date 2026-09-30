package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    long countByReadAtIsNull();

    List<Notification> findByReadAtIsNull();

    List<Notification> findTop30ByOrderByCreatedAtDesc();
}
