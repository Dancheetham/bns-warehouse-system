package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.enums.OrderStatus;

import java.util.List;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    /**
     * Units still owed against open orders that haven't been picked yet -
     * once picked, a unit moves to ALLOCATED status and is already excluded
     * from the AVAILABLE count naturally, so only the *unpicked* remainder
     * represents stock that's still sitting as AVAILABLE in our system but
     * has actually already been committed to an order. Needed so the
     * Shopify stock push never reports that stock as available when it's
     * already spoken for - Shopify decrements its own "available" the
     * moment an order is placed (not when it's fulfilled), so pushing our
     * raw on-shelf count during the window before we've picked an order
     * would incorrectly hand that commitment back to the storefront,
     * risking the same unit being sold twice.
     */
    @Query("SELECT ol.product.id, SUM(ol.quantityOrdered - ol.quantityPicked) FROM OrderLine ol "
            + "WHERE ol.order.status IN :openStatuses GROUP BY ol.product.id")
    List<Object[]> sumUnpickedQuantityByProduct(@Param("openStatuses") List<OrderStatus> openStatuses);
}
