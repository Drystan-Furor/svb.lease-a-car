package svb.leaseacar.lease;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** Thread-safe in-memory storage for vehicle versions. */
@Repository
class VehicleRepository {

  private final ConcurrentHashMap<UUID, Vehicle> vehicles = new ConcurrentHashMap<>();

  Vehicle create(VehicleRequest request) {
    UUID id = UUID.randomUUID();
    Vehicle vehicle = fromRequest(id, request);
    vehicles.put(id, vehicle);
    return vehicle;
  }

  Optional<Vehicle> findById(UUID id) {
    return Optional.ofNullable(vehicles.get(id));
  }

  Optional<Vehicle> replace(UUID id, VehicleRequest request) {
    return Optional.ofNullable(
        vehicles.computeIfPresent(id, (ignored, existing) -> fromRequest(id, request)));
  }

  boolean delete(UUID id) {
    return vehicles.remove(id) != null;
  }

  VehiclePage findPage(int page, int size) {
    List<Vehicle> snapshot =
        vehicles.values().stream()
            .sorted(Comparator.comparing(vehicle -> vehicle.id().toString()))
            .toList();
    long start = (long) page * size;
    if (start >= snapshot.size()) {
      return new VehiclePage(List.of(), page, size, snapshot.size());
    }
    int fromIndex = (int) start;
    int toIndex = Math.min(fromIndex + size, snapshot.size());
    return new VehiclePage(snapshot.subList(fromIndex, toIndex), page, size, snapshot.size());
  }

  private static Vehicle fromRequest(UUID id, VehicleRequest request) {
    return new Vehicle(
        id,
        request.make(),
        request.model(),
        request.version(),
        request.numberOfDoors(),
        request.co2Emission(),
        request.grossPrice(),
        request.nettPrice());
  }
}
