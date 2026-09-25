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

    // Same as above, but matching more than one order status - used by
    // PickingService.readyToPick() so a PARTIALLY_DESPATCHED order that's been
    // reopened for an extra shipment (new lines added to a locked order -
    // OrderService.update()) surfaces on the handheld again, alongside the
    // normal AWAITING_DESPATCH queue.
    java.util.List<Order> findByStatusInAndPickingStatusInOrderByOrderDateAsc(
            java.util.List<uk.co.bns.warehouse_api.enums.OrderStatus> statuses,
            java.util.List<uk.co.bns.warehouse_api.enums.PickingStatus> pickingStatuses);

    // Generate Invoices (InvoiceService) - orders currently sitting in
    // INVOICE_PENDING, split by ORDER vs CREDIT_REFUND per the Invoice/
    // Credit radio on that page.
    java.util.List<Order> findByStatusAndOrderTypeOrderByOrderDateAsc(
            uk.co.bns.warehouse_api.enums.OrderStatus status,
            uk.co.bns.warehouse_api.enums.OrderType orderType);

    // Same as above, but also picking up PARTIALLY_DESPATCHED orders - what's
    // actually shipped on one of those is invoiceable too, same as a fully
    // despatched (INVOICE_PENDING) order, just for a smaller quantity per
    // line (see InvoiceService.remainingQuantity).
    java.util.List<Order> findByStatusInAndOrderTypeOrderByOrderDateAsc(
            java.util.List<uk.co.bns.warehouse_api.enums.OrderStatus> statuses,
            uk.co.bns.warehouse_api.enums.OrderType orderType);

    // Delivery History (Sales) - every order that's actually gone out the
    // door at least once, whatever it's sitting at now (still awaiting
    // invoicing, fully invoiced/COMPLETED, or PARTIALLY_DESPATCHED and
    // waiting on the rest) - despatchedAt is set once, the first time
    // confirmDespatch() runs for an order, and never cleared by a later
    // partial re-despatch, so this always finds it.
    java.util.List<Order> findByDespatchedAtIsNotNullOrderByDespatchedAtDesc();
}
