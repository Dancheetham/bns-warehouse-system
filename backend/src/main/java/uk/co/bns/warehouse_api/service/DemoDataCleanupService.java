package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.repository.ProductRepository;

/**
 * Helper for TestDataResetService.clearDemoProductCatalog(). Each method runs in
 * its own fresh transaction (REQUIRES_NEW) - Postgres aborts an entire transaction
 * the moment any statement in it fails, so without this, one demo row that can't be
 * deleted would poison every attempt after it in the same batch.
 *
 * Deliberately checks for dependent rows BEFORE attempting a delete, rather than
 * trying the delete and catching the failure - a caught constraint-violation still
 * leaves Hibernate/Spring's transaction marked rollback-only internally, so trying
 * to recover from it in the same transaction throws UnexpectedRollbackException
 * regardless of the catch block. Checking first means the delete (or the
 * deactivate fallback) is the only thing that ever runs in a given transaction,
 * so nothing here can fail.
 *
 * Package-private on purpose - only meant to be called from TestDataResetService.
 */
@Service
@RequiredArgsConstructor
class DemoDataCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String tryDeleteOrder(String orderNumber) {
        Long orderId;
        try {
            orderId = jdbcTemplate.queryForObject("SELECT id FROM orders WHERE order_number = ?", Long.class, orderNumber);
        } catch (EmptyResultDataAccessException e) {
            return orderNumber + ": not found";
        }

        if (orderHasDependents(orderId)) {
            return orderNumber + ": left in place - something's been built on top of it (a pick, an RMA, etc.)";
        }

        int deletedLines = jdbcTemplate.update("DELETE FROM order_lines WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        return orderNumber + ": removed (" + deletedLines + " line(s))";
    }

    /**
     * Returns null if the product has dependents (still referenced), in which
     * case the caller should follow up with deactivateProduct in a fresh transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String tryDeleteProduct(String sku) {
        Product product = productRepository.findBySkuIgnoreCase(sku).orElse(null);
        if (product == null) return sku + ": already gone";
        if (productHasDependents(product.getId())) return null;
        productRepository.delete(product);
        return sku + ": removed";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String deactivateProduct(String sku) {
        Product product = productRepository.findBySkuIgnoreCase(sku).orElse(null);
        if (product == null) return sku + ": already gone";
        product.setActive(false);
        productRepository.save(product);
        return sku + ": still referenced by real data - deactivated instead of deleted";
    }

    private boolean orderHasDependents(Long orderId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM stock_items si JOIN order_lines ol ON si.order_line_id = ol.id WHERE ol.order_id = ?) +
                    (SELECT COUNT(*) FROM carton_lines cl JOIN order_lines ol ON cl.order_line_id = ol.id WHERE ol.order_id = ?) +
                    (SELECT COUNT(*) FROM rma_requests WHERE original_order_id = ? OR replacement_order_id = ? OR credit_order_id = ?)
                """, Integer.class, orderId, orderId, orderId, orderId, orderId);
        return count != null && count > 0;
    }

    private boolean productHasDependents(Long productId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM stock_items WHERE product_id = ?) +
                    (SELECT COUNT(*) FROM order_lines WHERE product_id = ?) +
                    (SELECT COUNT(*) FROM purchase_order_lines WHERE product_id = ?) +
                    (SELECT COUNT(*) FROM inventory WHERE product_id = ?) +
                    (SELECT COUNT(*) FROM rma_items WHERE product_id = ?)
                """, Integer.class, productId, productId, productId, productId, productId);
        return count != null && count > 0;
    }
}
