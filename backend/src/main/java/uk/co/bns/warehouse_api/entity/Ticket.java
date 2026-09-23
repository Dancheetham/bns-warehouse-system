package uk.co.bns.warehouse_api.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.bns.warehouse_api.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, unique = true)
    private String ticketNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "caller_name")
    private String callerName;

    private String phone;

    private String email;

    // Independent of order - a call can be a general account query with no
    // specific order behind it. Defaults to the linked order's own company
    // (see TicketService) but stays editable/removable on its own.
    @ManyToOne
    @JoinColumn(name = "company_id")
    @JsonIgnoreProperties({"creditLimit", "notes"})
    private Company company;

    // Optional - only set when the call is actually about a specific order
    // (e.g. "SO-10019 hasn't turned up").
    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnoreProperties({"lines", "company"})
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.IN_PROGRESS;

    // Manually editable, shown in the UI split as hours/minutes - a future
    // call-transcription API integration can also set this directly from the
    // recorded call duration (see TicketPublicController).
    @Column(name = "talk_time_minutes", nullable = false)
    private Integer talkTimeMinutes = 0;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketEntry> entries = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
