package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.StockItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared by OrderReversalService (reverseToDespatch/cancelAndReturnToStock)
 * and RmaService (receive) - whenever a unit that had already been synced to
 * GDMS moves back into stock for any reason, GDMS needs to be told to
 * recall/reclaim it, and StockItem.gdmsSyncedAt needs resetting so the next
 * time this exact unit goes out again, GdmsEndOfDayService treats it as
 * unsynced and sends it fresh (to whatever channel it ends up on next -
 * possibly a different customer/channel entirely from before).
 *
 * Deliberately best-effort: a GDMS outage, or GDMS rejecting some/all of the
 * MACs in the recall call, must never block the stock-return operation
 * itself (an RMA, a reversal, a cancellation) - the physical stock is back
 * in the warehouse either way, so gdmsSyncedAt is always reset regardless of
 * whether the GDMS side of it succeeded. A failed/skipped recall just means
 * the device keeps showing as assigned on GDMS's side until it's sorted out
 * there manually or naturally overwritten by a future assign - it does not
 * stop the warehouse operation. Every attempt (successful or not) is still
 * written to the GDMS sync log by GdmsChannelService, so a failed recall is
 * still visible there, not silently swallowed.
 */
@Service
@RequiredArgsConstructor
public class GdmsRecallService {

    private static final Logger log = LoggerFactory.getLogger(GdmsRecallService.class);

    private final GdmsChannelService gdmsChannelService;

    /**
     * Recalls every already-synced item in `items` from GDMS and resets its
     * gdmsSyncedAt, in one batched call. Items with no gdmsSyncedAt (never
     * synced, or an untracked/serial-only item with no MAC) are skipped
     * entirely - nothing to recall. `orderNumber` (the order, RMA, etc. this
     * return relates to) and `source` are only used for the GDMS sync log's
     * benefit; `orderNumber` may be null.
     */
    public void recallSyncedItems(List<StockItem> items, String orderNumber, String source) {
        List<StockItem> synced = items.stream()
                .filter(i -> i.getGdmsSyncedAt() != null)
                .filter(i -> i.getMacAddress() != null && !i.getMacAddress().isBlank())
                .toList();
        if (synced.isEmpty()) return;

        List<String> macs = synced.stream().map(StockItem::getMacAddress).toList();
        Map<String, String> macToOrderNumber = new HashMap<>();
        if (orderNumber != null) {
            for (String mac : macs) {
                macToOrderNumber.put(mac, orderNumber);
            }
        }

        try {
            gdmsChannelService.reclaimMacs(macs, macToOrderNumber, source);
        } catch (Exception e) {
            log.warn("GDMS recall failed for {} MAC(s) ({}): {}", macs.size(), source, e.getMessage());
        }

        // Reset regardless of the recall's own outcome - see class javadoc.
        for (StockItem item : synced) {
            item.setGdmsSyncedAt(null);
        }
    }
}
