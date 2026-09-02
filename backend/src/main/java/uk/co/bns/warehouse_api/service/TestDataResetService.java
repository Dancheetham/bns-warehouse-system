package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.exception.ForbiddenException;

import java.util.ArrayList;
import java.util.List;

/**
 * Wipes stock and purchase-order data for testing. Guarded by app.test-data-reset.enabled
 * (env var ALLOW_TEST_DATA_RESET), which defaults to false - this must be explicitly turned
 * on for a given environment (e.g. local/LAN testing) and should stay off anywhere real
 * data is trusted, since it's an irreversible bulk delete with no undo.
 *
 * Deliberately leaves products, locations and suppliers (reference data) alone, along with
 * orders, bug reports and API keys - only the tables that "stock" and "purchase order"
 * naturally cover are truncated.
 */
@Service
@RequiredArgsConstructor
public class TestDataResetService {

    private final JdbcTemplate jdbcTemplate;
    private final DemoDataCleanupService demoDataCleanupService;

    private static final List<String> DEMO_SKUS = List.of("GWN7802P", "GRP2615", "SFP-1G", "PATCH-CAT6-1M");
    private static final List<String> DEMO_ORDER_NUMBERS = List.of("SO-10001", "SO-10002", "SO-10003", "SO-10004");

    @Value("${app.test-data-reset.enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional
    public void resetStockAndPurchaseOrders() {
        requireEnabled();
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    goods_in_session_cartons,
                    goods_in_sessions,
                    stock_movements,
                    expected_stock_items,
                    expected_cartons,
                    stock_items,
                    inventory,
                    purchase_order_lines,
                    purchase_orders
                RESTART IDENTITY CASCADE
                """);
    }

    /**
     * Removes the four pre-seeded demo products (GWN7802P, GRP2615, SFP-1G,
     * PATCH-CAT6-1M) and their four demo sample orders, so real product data (e.g.
     * from Shopify) starts from a clean catalogue. Each row is handled in its own
     * transaction: a demo order is only deleted if nothing real has been built on
     * top of it (a pick, an RMA), and a demo product that's actually been used in
     * your own testing is deactivated instead of deleted, since it can't be safely
     * removed without losing that history. Nothing here can fail outright - you
     * always get a plain-English outcome for each row instead.
     */
    public List<String> clearDemoProductCatalog() {
        requireEnabled();
        List<String> summary = new ArrayList<>();

        for (String orderNumber : DEMO_ORDER_NUMBERS) {
            summary.add(demoDataCleanupService.tryDeleteOrder(orderNumber));
        }

        for (String sku : DEMO_SKUS) {
            String result = demoDataCleanupService.tryDeleteProduct(sku);
            summary.add(result != null ? result : demoDataCleanupService.deactivateProduct(sku));
        }

        return summary;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ForbiddenException(
                    "Test data reset is disabled on this environment - set ALLOW_TEST_DATA_RESET=true to enable it");
        }
    }
}
