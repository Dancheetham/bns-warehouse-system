package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per MAC per GDMS channel-assignment ("ASSIGN", /channel/assign) or
 * recall ("RECALL", /channel/recycle) attempt - a plain audit trail written
 * exclusively by GdmsChannelService, read only by GdmsSyncLogController for
 * the GDMS log page. Mirrors BugReport's shape/conventions.
 */
@Entity
@Table(name = "gdms_sync_log")
@Getter
@Setter
@NoArgsConstructor
public class GdmsSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    // ASSIGN or RECALL
    @Column(nullable = false, length = 20)
    private String operation;

    // e.g. "Scheduled", "Manual", "Manual (Order BNS-1234)",
    // "Auto (Reverse to Despatch)", "Auto (Cancelled)", "Auto (RMA)"
    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(name = "mac_address", nullable = false, length = 45)
    private String macAddress;

    @Column(name = "channel_id", length = 45)
    private String channelId;

    @Column(name = "channel_name")
    private String channelName;

    // SUCCESS or FAILURE
    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "error_reason", length = 500)
    private String errorReason;

    @PrePersist
    void prePersist() {
        if (this.attemptedAt == null) {
            this.attemptedAt = LocalDateTime.now();
        }
    }
}
