package svb.leaseacar.lease;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** Thread-safe in-memory storage for customers. */
@Repository
class CustomerRepository {

  private final ConcurrentHashMap<UUID, Customer> customers = new ConcurrentHashMap<>();

  Customer create(CustomerRequest request) {
    UUID id = UUID.randomUUID();
    Customer customer = fromRequest(id, request);
    customers.put(id, customer);
    return customer;
  }

  Optional<Customer> findById(UUID id) {
    return Optional.ofNullable(customers.get(id));
  }

  Optional<Customer> replace(UUID id, CustomerRequest request) {
    return Optional.ofNullable(
        customers.computeIfPresent(id, (ignored, existing) -> fromRequest(id, request)));
  }

  boolean delete(UUID id) {
    return customers.remove(id) != null;
  }

  CustomerPage findPage(int page, int size) {
    List<Customer> snapshot =
        customers.values().stream()
            .sorted(Comparator.comparing(customer -> customer.id().toString()))
            .toList();
    long start = (long) page * size;
    if (start >= snapshot.size()) {
      return new CustomerPage(List.of(), page, size, snapshot.size());
    }
    int fromIndex = (int) start;
    int toIndex = Math.min(fromIndex + size, snapshot.size());
    return new CustomerPage(snapshot.subList(fromIndex, toIndex), page, size, snapshot.size());
  }

  private static Customer fromRequest(UUID id, CustomerRequest request) {
    return new Customer(
        id,
        request.name(),
        request.street(),
        request.houseNumber(),
        request.zipcode(),
        request.place(),
        request.email(),
        request.phoneNumber());
  }
}
