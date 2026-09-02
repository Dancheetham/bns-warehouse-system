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
        String deliveryTown,
        String deliveryCountry,
        String deliveryPostcode,
        String deliveryCountryCode,
        @NotNull OrderStatus status,
        @NotNull OrderType orderType,
        BigDecimal shippingCost,
        String courierMethod,
        String specialInstructions,
        @NotEmpty @Valid List<OrderLineRequest> lines
) {}
