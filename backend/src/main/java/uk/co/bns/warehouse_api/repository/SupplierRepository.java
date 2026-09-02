package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}
