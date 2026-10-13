package svb.leaseacar.customer;

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

/** Exposes customer CRUD and bounded paging. */
@RestController
@RequestMapping("/customers")
class CustomerController {

  private final CustomerService service;

  CustomerController(CustomerService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<Customer> create(@Valid @RequestBody CustomerRequest request) {
    Customer customer = service.create(request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(customer.id())
            .toUri();
    return ResponseEntity.created(location).body(customer);
  }

  @GetMapping("/{id}")
  Customer find(@PathVariable UUID id) {
    return service.find(id);
  }

  @GetMapping
  CustomerPage list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.list(page, size);
  }

  @PutMapping("/{id}")
  Customer replace(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
    return service.replace(id, request);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
