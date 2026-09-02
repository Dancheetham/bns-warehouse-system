package uk.co.bns.warehouse_api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "goods_in_session_cartons", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "expected_carton_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class GoodsInSessionCarton {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private GoodsInSession session;

    @ManyToOne
    @JoinColumn(name = "expected_carton_id", nullable = false)
    private ExpectedCarton expectedCarton;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    @PrePersist
    void prePersist() {
        this.scannedAt = LocalDateTime.now();
    }
}
