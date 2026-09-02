package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.LocationRequest;
import uk.co.bns.warehouse_api.entity.Location;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.LocationRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    public List<Location> findAll() {
        return locationRepository.findAll();
    }

    public Location findById(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Location " + id + " not found"));
    }

    public Location create(LocationRequest request) {
        Location location = new Location();
        location.setCode(request.code());
        location.setDescription(request.description());
        return locationRepository.save(location);
    }
}
