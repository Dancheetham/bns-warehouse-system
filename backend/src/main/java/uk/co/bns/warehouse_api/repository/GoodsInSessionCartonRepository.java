package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.GoodsInSessionCarton;

import java.util.Optional;

public interface GoodsInSessionCartonRepository extends JpaRepository<GoodsInSessionCarton, Long> {
    Optional<GoodsInSessionCarton> findBySession_IdAndExpectedCarton_Id(Long sessionId, Long expectedCartonId);
}
