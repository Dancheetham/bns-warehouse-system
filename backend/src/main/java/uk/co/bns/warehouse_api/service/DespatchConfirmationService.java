package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.AcknowledgementResult;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.entity.StockItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "your order has shipped" email - sent from here rather than relying on
 * Shopify's own notification, since this one carries the MAC address, serial
 * number and default password for each device shipped - content Shopify's
 * fixed email template has no way to include. ShopifyFulfillmentService still
 * updates the order's status on Shopify (with notifyCustomer: false), so the
 * customer's account correctly shows it as shipped; this is the only email
 * that actually goes to them about it.
 */
@Service
@RequiredArgsConstructor
public class DespatchConfirmationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${mail.from-address}")
    private String fromAddress;

    public AcknowledgementResult sendDespatchConfirmation(Order order, List<StockItem> despatchedItems) {
        String subject = "Your Order Has Shipped - " + order.getOrderNumber();
        String body = composeBody(order, despatchedItems);

        if (order.getCustomerEmail() == null || order.getCustomerEmail().isBlank()) {
            return new AcknowledgementResult(false, "No customer email address is set on this order", null, subject, body);
        }

        if (mailSender == null) {
            return new AcknowledgementResult(
                    false,
                    "SMTP is not configured (set SMTP_HOST) - email was not actually sent, but here's what would have gone out",
                    order.getCustomerEmail(), subject, body);
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(order.getCustomerEmail());
            message.setFrom(fromAddress);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return new AcknowledgementResult(true, "Sent", order.getCustomerEmail(), subject, body);
        } catch (Exception e) {
            return new AcknowledgementResult(false, "Failed to send: " + e.getMessage(), order.getCustomerEmail(), subject, body);
        }
    }

    private String composeBody(Order order, List<StockItem> despatchedItems) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(order.getCustomerName()).append(",\n\n");
        sb.append("Your order ").append(order.getOrderNumber()).append(" has been despatched.\n\n");

        // Group despatched units by product, in order line order - one heading
        // per product, with each unit's identifier and login details under it.
        Map<Product, List<StockItem>> byProduct = new LinkedHashMap<>();
        for (StockItem item : despatchedItems) {
            byProduct.computeIfAbsent(item.getProduct(), p -> new java.util.ArrayList<>()).add(item);
        }

        for (Map.Entry<Product, List<StockItem>> entry : byProduct.entrySet()) {
            Product product = entry.getKey();
            List<StockItem> items = entry.getValue();
            sb.append(product.getName()).append(" (").append(product.getSku()).append(")\n");

            boolean anyIdentifiers = items.stream().anyMatch(i -> i.getMacAddress() != null || i.getSerialNumber() != null);
            if (anyIdentifiers) {
                for (StockItem item : items) {
                    sb.append("  - ");
                    if (item.getMacAddress() != null) sb.append("MAC: ").append(item.getMacAddress()).append("  ");
                    if (item.getSerialNumber() != null) sb.append("Serial: ").append(item.getSerialNumber()).append("  ");
                    if (product.getDefaultPassword() != null && !product.getDefaultPassword().isBlank()) {
                        sb.append("Default password: ").append(product.getDefaultPassword());
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("  - Quantity: ").append(items.size()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("We'd recommend changing the default password on each device on first login.\n\n");
        sb.append("Kind regards,\nBNS Distribution\n");
        return sb.toString();
    }
}
