package uk.co.bns.warehouse_api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "rma_items")
@Getter
@Setter
@NoArgsConstructor
public class RmaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "rma_request_id", nullable = false)
    @JsonIgnore
    private RmaRequest rmaRequest;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "identifier")
    private String identifier;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(nullable = false)
    private Boolean faulty = false;

    @Column(name = "grandstream_ticket_number")
    private String grandstreamTicketNumber;

    @Column(name = "reason_for_return", columnDefinition = "TEXT")
    private String reasonForReturn;

    @ManyToOne
    @JoinColumn(name = "matched_order_id")
    private Order matchedOrder;

    @ManyToOne
    @JoinColumn(name = "matched_order_line_id")
    private OrderLine matchedOrderLine;

    @Column(name = "matched_unit_price", precision = 12, scale = 2)
    private BigDecimal matchedUnitPrice;

    // Whichever return window applies to this item - the (default 28-day)
    // non-faulty return window if faulty is false, the (default 1-year) RTB
    // warranty if true. Both windows are configurable in Settings; this is
    // computed once at submission from the matched order's date.
    @Column(name = "return_window_expires_at")
    private LocalDate returnWindowExpiresAt;

    @Column(name = "grandstream_warranty_checked", nullable = false)
    private Boolean grandstreamWarrantyChecked = false;

    @Column(nullable = false)
    private Boolean received = false;

    @Column(name = "rsf_applied", nullable = false)
    private Boolean rsfApplied = false;

    @Column(nullable = false)
    private Boolean credited = false;
}
