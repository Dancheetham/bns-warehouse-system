package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.ProductRequest;
import uk.co.bns.warehouse_api.entity.Location;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.exception.ConflictException;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.LocationRepository;
import uk.co.bns.warehouse_api.repository.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    @Transactional
    public Product create(ProductRequest request) {
        String normalizedSku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new ConflictException("A product with SKU " + normalizedSku + " already exists");
        }
        Product product = new Product();
        apply(product, request);
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product product = findById(id);
        apply(product, request);
        // A human editing and saving the product through the normal form is the
        // signal that it's been reviewed - clears the flag a Shopify sync set.
        product.setNeedsReview(false);
        return productRepository.save(product);
    }

    private void apply(Product product, ProductRequest request) {
        product.setSku(request.sku().trim().toUpperCase());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setTrackingType(request.trackingType());
        // defaultPassword is intentionally left exactly as entered - passwords are
        // case-sensitive and must never be normalised.
        product.setDefaultPassword(request.defaultPassword());
        product.setWeightKg(request.weightKg());
        if (request.active() != null) {
            product.setActive(request.active());
        }

        if (request.defaultLocationId() != null) {
            Location location = locationRepository.findById(request.defaultLocationId())
                    .orElseThrow(() -> new NotFoundException("Location " + request.defaultLocationId() + " not found"));
            product.setDefaultLocation(location);
        } else {
            product.setDefaultLocation(null);
        }
    }
}
