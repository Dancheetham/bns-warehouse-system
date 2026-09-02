package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    java.util.Optional<Company> findByShopifyCompanyId(String shopifyCompanyId);
}
