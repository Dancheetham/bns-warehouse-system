package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bug_reports")
@Getter
@Setter
@NoArgsConstructor
public class BugReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // AUTO - captured automatically when an API call fails in the UI
    // MANUAL - logged by hand by a user
    @Column(nullable = false)
    private String source = "MANUAL";

    @Column(name = "error_code")
    private String errorCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // e.g. "POST /api/stock-items/move" - what was being attempted when it happened
    @Column(length = 500)
    private String context;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.occurredAt == null) {
            this.occurredAt = this.createdAt;
        }
    }
}
