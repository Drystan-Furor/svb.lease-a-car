package svb.leaseacar.customer;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Coordinates customer CRUD operations and paging rules. */
@Service
class CustomerService {

  static final int MAX_PAGE_SIZE = 100;

  private final CustomerRepository repository;

  CustomerService(CustomerRepository repository) {
    this.repository = repository;
  }

  Customer create(CustomerRequest request) {
    return repository.create(request);
  }

  Customer find(UUID id) {
    return repository.findById(id).orElseThrow(CustomerService::notFound);
  }

  Customer replace(UUID id, CustomerRequest request) {
    return repository.replace(id, request).orElseThrow(CustomerService::notFound);
  }

  void delete(UUID id) {
    if (!repository.delete(id)) {
      throw notFound();
    }
  }

  CustomerPage list(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "page must be at least 0 and size must be between 1 and 100");
    }
    return repository.findPage(page, size);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
  }
}
