package svb.leaseacar.lease;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Coordinates vehicle CRUD operations and paging rules. */
@Service
class VehicleService {

  static final int MAX_PAGE_SIZE = 100;

  private final VehicleRepository repository;

  VehicleService(VehicleRepository repository) {
    this.repository = repository;
  }

  Vehicle create(VehicleRequest request) {
    return repository.create(request);
  }

  Vehicle find(UUID id) {
    return repository.findById(id).orElseThrow(VehicleService::notFound);
  }

  Vehicle replace(UUID id, VehicleRequest request) {
    return repository.replace(id, request).orElseThrow(VehicleService::notFound);
  }

  void delete(UUID id) {
    if (!repository.delete(id)) {
      throw notFound();
    }
  }

  VehiclePage list(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "page must be at least 0 and size must be between 1 and 100");
    }
    return repository.findPage(page, size);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
  }
}
