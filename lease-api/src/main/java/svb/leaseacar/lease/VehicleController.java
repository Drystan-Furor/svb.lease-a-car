package svb.leaseacar.lease;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Exposes vehicle-version CRUD and bounded paging. */
@RestController
@RequestMapping("/vehicles")
class VehicleController {

  private final VehicleService service;

  VehicleController(VehicleService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<Vehicle> create(@Valid @RequestBody VehicleRequest request) {
    Vehicle vehicle = service.create(request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(vehicle.id())
            .toUri();
    return ResponseEntity.created(location).body(vehicle);
  }

  @GetMapping("/{id}")
  Vehicle find(@PathVariable UUID id) {
    return service.find(id);
  }

  @GetMapping
  VehiclePage list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.list(page, size);
  }

  @PutMapping("/{id}")
  Vehicle replace(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
    return service.replace(id, request);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
