package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.AcknowledgementResult;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.OrderRepository;

import java.time.LocalDateTime;

/**
 * Composes the order acknowledgement email and sends it if SMTP is configured
 * (SMTP_HOST env var). If it isn't, this still returns the composed subject/body
 * so the content is visible and correct - it just says plainly that nothing was
 * actually delivered, rather than silently pretending to send.
 */
@Service
@RequiredArgsConstructor
public class AcknowledgementService {

    private final OrderRepository orderRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${mail.from-address}")
    private String fromAddress;

    @Transactional
    public AcknowledgementResult sendAcknowledgement(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));

        String subject = "Order Acknowledgement - " + order.getOrderNumber();
        String body = composeBody(order);

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

            order.setAcknowledgementSentAt(LocalDateTime.now());
            orderRepository.save(order);

            return new AcknowledgementResult(true, "Sent", order.getCustomerEmail(), subject, body);
        } catch (Exception e) {
            return new AcknowledgementResult(false, "Failed to send: " + e.getMessage(), order.getCustomerEmail(), subject, body);
        }
    }

    private String composeBody(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(order.getCustomerName()).append(",\n\n");
        sb.append("Thank you for your order ").append(order.getOrderNumber());
        if (order.getOrderReference() != null && !order.getOrderReference().isBlank()) {
            sb.append(" (your reference: ").append(order.getOrderReference()).append(")");
        }
        sb.append(". This confirms we've received it and it's now being processed for despatch.\n\n");
        if (order.getCourierMethod() != null && !order.getCourierMethod().isBlank()) {
            sb.append("Shipping method: ").append(order.getCourierMethod()).append("\n\n");
        }
        sb.append("Order contents:\n");
        for (OrderLine line : order.getLines()) {
            sb.append("  - ").append(line.getQuantityOrdered()).append(" x ")
                    .append(line.getProduct().getName()).append(" (").append(line.getProduct().getSku()).append(")\n");
        }
        sb.append("\nKind regards,\nBNS Distribution\n");
        return sb.toString();
    }
}
