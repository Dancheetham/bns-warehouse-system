package uk.co.bns.warehouse_api.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A person at a Company - permanently kept (not just a one-off import
 * artifact) so they're searchable and linkable when opening a support
 * ticket, mirroring how Company/Order already link to a Ticket. The
 * shopifyCustomerId/invitedToShopifyAt fields are unused until the future
 * Shopify B2B "Company Contacts" sync/invite-email feature is built, but are
 * added now so that feature needs no further schema change.
 */
@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnoreProperties({"creditLimit", "notes"})
    private Company company;

    @Column(nullable = false)
    private String name;

    private String email;

    private String phone;

    private String position;

    @Column(name = "main_contact", nullable = false)
    private boolean mainContact = false;

    @Column(nullable = false)
    private boolean active = true;

    // Set once a future Shopify B2B invite has actually been sent for this
    // contact - not used by anything yet.
    @Column(name = "shopify_customer_id")
    private String shopifyCustomerId;

    @Column(name = "invited_to_shopify_at")
    private LocalDateTime invitedToShopifyAt;

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
