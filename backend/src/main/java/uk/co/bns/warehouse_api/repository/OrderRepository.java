package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Order;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    boolean existsByOrderNumber(String orderNumber);
    boolean existsByEcommerceOrderNumber(String ecommerceOrderNumber);

    java.util.List<Order> findByCompany_Id(Long companyId);

    // Orders synced before the shopifyOrderId (Shopify's GraphQL id) started
    // being captured - self-healed by ShopifyOrderSyncService on every sync.
    java.util.List<Order> findByEcommerceOrderNumberIsNotNullAndShopifyOrderIdIsNull();

    java.util.List<Order> findByStatusAndPickingStatusInOrderByOrderDateAsc(
            uk.co.bns.warehouse_api.enums.OrderStatus status,
            java.util.List<uk.co.bns.warehouse_api.enums.PickingStatus> pickingStatuses);
}
