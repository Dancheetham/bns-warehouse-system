package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.entity.Order;

/**
 * Response for both "Reverse to Despatch" and "Cancel & Return to Stock"
 * (OrderReversalService/OrderReversalController) - the stock-side reversal
 * itself either succeeds or throws (same as before, v0.142 added nothing
 * new there), but an order shipped with APC now also gets a best-effort
 * attempt to void the actual APC booking via their CancelOrder endpoint
 * (ApcShippingService.cancelOrder) before its waybill is cleared, so BNS
 * isn't left paying for a manifested parcel nobody's sending.
 *
 * `courierWarning` carries that attempt's outcome ONLY when it's worth
 * flagging - i.e. the attempt was made (the order had an apcWaybill) and it
 * failed (most likely because APC had already manifested it - their own
 * docs are explicit that cancellation only works up to that point, which
 * lines up with what Dan described: that's also the point APC starts
 * billing for it). Null means either there was nothing to cancel (no APC
 * waybill - including every DPD order, since DPD's API has no cancel/void
 * endpoint at all, see the comment in OrderReversalService) or the cancel
 * genuinely succeeded. The reversal itself is never blocked by this - stock
 * has already been put back by the time the courier call is attempted.
 */
public record OrderReversalResult(Order order, String courierWarning) {}
