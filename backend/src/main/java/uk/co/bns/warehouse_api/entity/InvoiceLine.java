package uk.co.bns.warehouse_api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line on a generated Invoice - a snapshot (SKU/description/price at
 * the time of generation) rather than a live join through orderLine, for
 * the same reason Invoice.customerName is a snapshot: a product rename
 * later shouldn't silently change what an already-issued invoice appears
 * to say. orderLine/order are kept purely for traceability (so "what did
 * this invoice actually cover" can be answered from the order screen too).
 */
@Entity
@Table(name = "invoice_lines")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonIgnore
    private Invoice invoice;

    // Null for a shipping line (see "shipping" below) - there's no OrderLine
    // behind an order's delivery charge, only the Order itself.
    @ManyToOne
    @JoinColumn(name = "order_line_id")
    private OrderLine orderLine;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private String sku;

    private String description;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "net_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal netAmount;

    @Column(name = "vat_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal vatAmount;

    // True for the one line per order that bills its delivery/shipping cost
    // (see Order.shippingInvoiced) rather than a product - lets the PDF and
    // any future reporting tell it apart from an ordinary product line
    // without guessing from the SKU text.
    @Column(nullable = false)
    private boolean shipping = false;
}
