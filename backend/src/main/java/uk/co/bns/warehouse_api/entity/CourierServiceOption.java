package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One courier service code ("DPD" networkKey, or "APC" product code) this
 * account has ever actually been offered - live or via the "Refresh from
 * live lookup" sweep (DpdShippingService/ApcShippingService.
 * refreshAllKnownServices()) - plus whether it's currently enabled on
 * Settings > Couriers > {DPD,APC} > Available Services.
 *
 * Replaces an earlier approach (pre-v0.140) that serialized the whole known-
 * services list as a JSON blob into a single Settings row
 * (dpd_last_known_services/apc_last_known_services), with disabled codes
 * tracked separately as a comma-separated list
 * (dpd_disabled_services/apc_disabled_services). That JSON blob was capped
 * by the settings table's VARCHAR(500) column - easily exceeded once a few
 * sweep postcodes' worth of services accumulated - and the save failure
 * this caused was being silently swallowed by cacheLastKnownServices()'s
 * try/catch, so a sweep could report zero errors while never actually
 * growing the list. A real row per courier+code has no such ceiling, and
 * `enabled` lives right on the row instead of a separate list that has to
 * be kept in sync with it.
 *
 * `label`/`extraLabel` cover both couriers' shapes: DPD's networkDesc goes
 * in `label` and serviceDesc in `extraLabel`; APC only has one description,
 * stored in `label` with `extraLabel` left null.
 */
@Entity
@Table(name = "courier_service_options")
@Getter
@Setter
@NoArgsConstructor
public class CourierServiceOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "DPD" or "APC" - plain text rather than an enum/foreign key, same
    // reasoning as Order.collectionCourierName: cheap, and nothing here
    // needs to join against a courier table.
    @Column(nullable = false, length = 10)
    private String courier;

    // DPD's networkKey, or APC's product code.
    @Column(nullable = false, length = 50)
    private String code;

    // TEXT, not a capped VARCHAR - deliberately, so a long service
    // description can never silently fail to save the way the old
    // VARCHAR(500) settings-blob cache did (see the class comment above).
    @Column(nullable = false, columnDefinition = "TEXT")
    private String label = "";

    @Column(name = "extra_label", columnDefinition = "TEXT")
    private String extraLabel;

    @Column(nullable = false)
    private boolean enabled = true;

    public CourierServiceOption(String courier, String code, String label, String extraLabel, boolean enabled) {
        this.courier = courier;
        this.code = code;
        this.label = label != null ? label : "";
        this.extraLabel = extraLabel;
        this.enabled = enabled;
    }
}
