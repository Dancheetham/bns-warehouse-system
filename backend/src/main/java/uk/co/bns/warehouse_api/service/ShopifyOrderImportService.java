package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.OrderType;
import uk.co.bns.warehouse_api.repository.CompanyRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.ProductRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Each order's import runs in its own fresh transaction (REQUIRES_NEW) - the
 * same lesson learned from the RMA demo-data cleanup: without this, one order
 * that fails for any reason poisons the whole batch's transaction, losing every
 * other order already processed in that same sync run, not just the bad one.
 *
 * Package-private on purpose - only meant to be called from ShopifyOrderSyncService.
 */
@Service
@RequiredArgsConstructor
class ShopifyOrderImportService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final OrderService orderService;

    /** Returns null on success, or a reason string if the order was skipped/failed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String importOrder(JsonNode node, String shopifyOrderNumber) {
        if (shopifyOrderNumber != null && orderRepository.existsByEcommerceOrderNumber(shopifyOrderNumber)) {
            return null;
        }

        JsonNode lineItemEdges = node.path("lineItems").path("edges");
        List<OrderLine> lines = new ArrayList<>();
        for (JsonNode lineEdge : lineItemEdges) {
            JsonNode item = lineEdge.path("node");
            String sku = item.path("sku").asText("");
            if (sku.isBlank()) {
                return shopifyOrderNumber + ": skipped - a line item has no SKU set";
            }
            Optional<Product> product = productRepository.findBySkuIgnoreCase(sku);
            if (product.isEmpty()) {
                return shopifyOrderNumber + ": skipped - SKU " + sku + " not found in the product catalogue";
            }

            OrderLine line = new OrderLine();
            line.setProduct(product.get());
            line.setQuantityOrdered(item.path("quantity").asInt(0));
            line.setQuantityDespatched(0);
            line.setUnitPrice(toBigDecimal(item.path("originalUnitPriceSet").path("shopMoney").path("amount")));
            line.setNotes(item.path("name").asText(null));
            lines.add(line);
        }
        if (lines.isEmpty()) {
            return shopifyOrderNumber + ": skipped - no line items";
        }

        Order order = new Order();
        order.setOrderNumber(orderService.generateOrderNumber());
        order.setEcommerceOrderNumber(shopifyOrderNumber);
        order.setShopifyOrderId(node.path("id").asText(null));
        order.setOrderDate(parseShopifyDate(node.path("createdAt").asText(null)));
        order.setOrderReference(node.path("poNumber").asText(null));

        // customerName stays the fallback/generic name (kept non-null); orderedBy
        // and customerEmail prefer purchasingEntity, which for a B2B order gives
        // the specific person who placed it (via the company contact) rather than
        // the top-level customer field, which isn't reliably that same person.
        JsonNode customer = node.path("customer");
        String firstName = customer.path("firstName").asText("");
        String lastName = customer.path("lastName").asText("");
        String fallbackName = (firstName + " " + lastName).trim();
        order.setCustomerName(fallbackName.isBlank() ? "Shopify Customer" : fallbackName);
        order.setCustomerEmail(customer.path("email").asText(null));

        JsonNode purchasingEntity = node.path("purchasingEntity");
        String purchasingType = purchasingEntity.path("__typename").asText("");
        if ("PurchasingCompany".equals(purchasingType)) {
            JsonNode contactCustomer = purchasingEntity.path("contact").path("customer");
            if (!contactCustomer.isMissingNode() && !contactCustomer.isNull()) {
                order.setOrderedBy(contactCustomer.path("displayName").asText(null));
                String contactEmail = contactCustomer.path("email").asText(null);
                if (contactEmail != null) order.setCustomerEmail(contactEmail);
            }
        } else if ("Customer".equals(purchasingType)) {
            order.setOrderedBy(purchasingEntity.path("displayName").asText(null));
        } else {
            order.setOrderedBy(fallbackName.isBlank() ? null : fallbackName);
        }

        JsonNode shipping = node.path("shippingAddress");
        order.setDeliveryName(shipping.path("name").asText(null));
        order.setDeliveryTown(shipping.path("city").asText(null));
        order.setDeliveryCountry(shipping.path("country").asText(null));
        order.setDeliveryPostcode(shipping.path("zip").asText(null));
        order.setDeliveryCountryCode(shipping.path("countryCodeV2").asText(null));

        order.setShippingCost(toBigDecimal(node.path("totalShippingPriceSet").path("shopMoney").path("amount")));
        order.setSpecialInstructions(node.path("note").asText(null));
        order.setStatus(OrderStatus.ON_HOLD);
        order.setOrderType(OrderType.ORDER);

        JsonNode purchasingCompanyId = node.path("purchasingEntity").path("company").path("id");
        if (!purchasingCompanyId.isMissingNode() && !purchasingCompanyId.isNull()) {
            String companyGid = purchasingCompanyId.asText();
            Company company = companyRepository.findByShopifyCompanyId(companyGid).orElse(null);
            order.setCompany(company);
        }

        for (OrderLine line : lines) {
            line.setOrder(order);
            order.getLines().add(line);
        }

        orderRepository.save(order);
        return null;
    }

    private BigDecimal toBigDecimal(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        String text = node.asText(null);
        if (text == null || text.isBlank()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime parseShopifyDate(String iso) {
        if (iso == null) return LocalDateTime.now();
        try {
            return java.time.OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
