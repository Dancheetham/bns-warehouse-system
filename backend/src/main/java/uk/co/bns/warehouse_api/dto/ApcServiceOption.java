package uk.co.bns.warehouse_api.dto;

// One APC Overnight product code offered on the order screen's Service
// dropdown when courierType == APC - normally APC's own live, weight/size-
// aware answer from ServiceAvailability.json (see
// ApcShippingService.checkServiceAvailability), so a sub-1kg order
// correctly offers MailPack/CourierPack/Parcel while a 10kg order only
// offers Parcel, same as APC's own weight rules. Falls back to a cached or
// static list (see ApcServiceLookupResult) if the live call fails, always
// paired with a free-text override on the order screen for anything not
// listed.
public record ApcServiceOption(String code, String description) {}
