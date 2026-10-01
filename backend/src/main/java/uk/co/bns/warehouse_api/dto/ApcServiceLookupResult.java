package uk.co.bns.warehouse_api.dto;

import java.util.List;

/**
 * Response for the "Service" dropdown on the order screen when courierType
 * == APC. `live` is true only when this list came straight from APC's own
 * ServiceAvailability.json for this order's actual delivery address and
 * weight just now - which is also what makes it weight-aware (e.g. only
 * offering MailPack/CourierPack for a sub-1kg/sub-5kg order, same as APC's
 * own Hypaship website would). When that live call fails, falls back to the
 * last list APC returned successfully for ANY order (cached in the
 * courier_service_options table), and finally to a small hardcoded list of common
 * weekday product codes if nothing's ever been cached - mirrors
 * DpdServiceLookupResult exactly, see ApcShippingService.checkServiceAvailability.
 */
public record ApcServiceLookupResult(List<ApcServiceOption> services, boolean live, String liveError) {}
