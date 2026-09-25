package uk.co.bns.warehouse_api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderRequest(
        String orderNumber,
        @NotNull LocalDateTime orderDate,
        @NotBlank String customerName,
        String customerEmail,
        Long companyId,
        String orderReference,
        String ecommerceOrderNumber,
        String orderedBy,
        String deliveryName,
        String deliveryAddressLine1,
        String deliveryAddressLine2,
        String deliveryPhone,
        String deliveryTown,
        String deliveryCountry,
        String deliveryPostcode,
        String deliveryCountryCode,
        @NotNull OrderStatus status,
        @NotNull OrderType orderType,
        BigDecimal shippingCost,
        String courierMethod,
        // Optional - the general update path can now carry the DPD service
        // choice too (previously only releaseForDespatch could set it), so
        // it can be changed after release without going through that
        // ON_HOLD-only endpoint. Null/blank means "leave whatever's there" -
        // see OrderService.applyFields, which mirrors releaseForDespatch's
        // own blank-is-a-no-op handling rather than wiping it out.
        String dpdNetworkKey,
        String specialInstructions,
        // Only meaningful on update, not create - the version the editor
        // loaded, so a stale save (someone else has saved since) can be
        // rejected with a clear conflict rather than silently overwriting
        // their change. Null is treated as "don't check" (new orders, and
        // any older client that hasn't been updated to send it yet).
        Long version,
        @NotEmpty @Valid List<OrderLineRequest> lines
) {}
