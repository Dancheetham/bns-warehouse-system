package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.bns.warehouse_api.enums.GoodsInSessionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "goods_in_sessions")
@Getter
@Setter
@NoArgsConstructor
public class GoodsInSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoodsInSessionStatus status = GoodsInSessionStatus.OPEN;

    @Column(name = "started_by")
    private String startedBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "saved_by")
    private String savedBy;

    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsInSessionCarton> scannedCartons = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.startedAt = LocalDateTime.now();
    }
}
