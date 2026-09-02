package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Product;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    Optional<Product> findBySkuIgnoreCase(String sku);
    boolean existsBySku(String sku);
    boolean existsBySkuIgnoreCase(String sku);
    Optional<Product> findByShopifyVariantId(String shopifyVariantId);
    long countByNeedsReviewTrue();
    long countByShopifyProductIdIsNotNull();
}
