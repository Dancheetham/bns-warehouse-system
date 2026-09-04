package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.bns.warehouse_api.enums.StockItemStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_items")
@Getter
@Setter
@NoArgsConstructor
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "mac_address")
    private String macAddress;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "wifi_mac_address")
    private String wifiMacAddress;

    @Column(name = "batch_code")
    private String batchCode;

    // Per unit, not per product - e.g. every GRP2601 has its own distinct
    // default password. Captured at goods-in if the supplier's shipment
    // spreadsheet provides one (optional PASSWORD column).
    @Column(name = "default_password")
    private String defaultPassword;

    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockItemStatus status = StockItemStatus.AVAILABLE;

    @Column(nullable = false)
    private Boolean quarantined = false;

    @Column(name = "quarantine_reason")
    private String quarantineReason;

    @ManyToOne
    @JoinColumn(name = "purchase_order_line_id")
    private PurchaseOrderLine purchaseOrderLine;

    // Set once a handheld pick allocates this unit to an order - cleared again if
    // the pick is undone. Distinct from purchaseOrderLine, which is about receiving.
    @ManyToOne
    @JoinColumn(name = "order_line_id")
    private OrderLine orderLine;

    // Only populated when Settings > Packing Mode is SERIAL - assigns this specific
    // unit to a physical carton. In SPLIT mode (the default), packing is tracked
    // via CartonLine instead and this stays null.
    @ManyToOne
    @JoinColumn(name = "carton_id")
    private Carton carton;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
