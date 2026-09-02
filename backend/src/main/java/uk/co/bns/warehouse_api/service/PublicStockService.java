package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.PublicStockItem;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.ProductRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;

import java.util.List;

/**
 * "Available" is computed live from StockItem status counts (the same source of
 * truth the internal Stock Overview screen uses) rather than the bulk Inventory
 * table, so there's only ever one place this number can drift from reality.
 */
@Service
@RequiredArgsConstructor
public class PublicStockService {

    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;

    public List<PublicStockItem> getAllAvailableStock() {
        return productRepository.findAll().stream()
                .filter(Product::getActive)
                .map(this::toPublicStockItem)
                .toList();
    }

    public PublicStockItem getStockForSku(String sku) {
        Product product = productRepository.findBySkuIgnoreCase(sku)
                .orElseThrow(() -> new NotFoundException("No product found for SKU " + sku));
        return toPublicStockItem(product);
    }

    private PublicStockItem toPublicStockItem(Product product) {
        long available = stockItemRepository.countByProduct_IdAndStatus(product.getId(), StockItemStatus.AVAILABLE);
        return new PublicStockItem(product.getSku(), product.getName(), (int) available);
    }
}
