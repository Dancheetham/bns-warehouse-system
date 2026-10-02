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

    // Delivery History (Sales) - deliberately NOT every order with
    // despatchedAt set. Both reversal actions (Reverse to Despatch, Cancel &
    // Return to Stock) can still be used right up until the point an order
    // reaches INVOICE_PENDING/COMPLETED in practice, so an order sitting
    // anywhere else (ON_HOLD, AWAITING_DESPATCH, PARTIALLY_DESPATCHED) may
    // only have been despatched as part of testing/a correction that got
    // reversed, not a real delivery. Restricting to these two statuses
    // means a row here only ever represents stock that's genuinely,
    // irreversibly out the door - see Dan's reasoning 2026-10-02.
    java.util.List<Order> findByDespatchedAtIsNotNullAndStatusInOrderByDespatchedAtDesc(
            java.util.List<uk.co.bns.warehouse_api.enums.OrderStatus> statuses);
}
