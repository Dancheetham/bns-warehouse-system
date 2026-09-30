package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.CollectionCourierOptionRequest;
import uk.co.bns.warehouse_api.entity.CollectionCourierOption;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CollectionCourierOptionRepository;

import java.util.List;

/**
 * Admin CRUD for the "Collection" courier list (Settings > Couriers) plus
 * the active-only list the order screen's Collection dropdown reads.
 * Deletes are hard deletes - these are just labels referenced from orders by
 * name (Order.collectionCourierName), never by id, so removing one never
 * orphans a historical order's own copy of the name.
 */
@Service
@RequiredArgsConstructor
public class CollectionCourierOptionService {

    private final CollectionCourierOptionRepository repository;

    public List<CollectionCourierOption> findAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc();
    }

    public List<CollectionCourierOption> findActive() {
        return repository.findByActiveTrueOrderBySortOrderAscNameAsc();
    }

    public CollectionCourierOption create(CollectionCourierOptionRequest request) {
        if (repository.findAllByOrderBySortOrderAscNameAsc().stream()
                .anyMatch(o -> o.getName().equalsIgnoreCase(request.name().trim()))) {
            throw new ValidationException("A Collection courier called \"" + request.name() + "\" already exists");
        }
        CollectionCourierOption option = new CollectionCourierOption();
        option.setName(request.name().trim());
        option.setActive(request.active());
        option.setSortOrder(request.sortOrder());
        return repository.save(option);
    }

    public CollectionCourierOption update(Long id, CollectionCourierOptionRequest request) {
        CollectionCourierOption option = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Collection courier " + id + " not found"));
        option.setName(request.name().trim());
        option.setActive(request.active());
        option.setSortOrder(request.sortOrder());
        return repository.save(option);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Collection courier " + id + " not found");
        }
        repository.deleteById(id);
    }
}
