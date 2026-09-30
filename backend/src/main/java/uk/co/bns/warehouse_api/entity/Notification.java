package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * General-purpose notification shown via the bell icon - not GDMS-specific
 * by design, so future notification types (and a future reminder system)
 * can reuse this table/bell rather than each needing their own. `link` is a
 * relative frontend path (with query string) the UI navigates to on click.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A short machine tag, e.g. "GDMS_FAILURE" - lets the frontend (or a
    // future type-specific icon/grouping) distinguish notification kinds
    // without parsing the message text.
    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 500)
    private String link;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
