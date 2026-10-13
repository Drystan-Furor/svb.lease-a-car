package svb.leaseacar.interest.rate;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
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

/** Exposes interest-rate CRUD and historical selection. */
@RestController
@RequestMapping("/interest-rates")
class InterestRateController {

  private final InterestRateService service;

  InterestRateController(InterestRateService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<InterestRate> create(@Valid @RequestBody InterestRateRequest request) {
    InterestRate rate = service.create(request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(rate.id())
            .toUri();
    return ResponseEntity.created(location).body(rate);
  }

  @GetMapping("/{id}")
  InterestRate find(@PathVariable UUID id) {
    return service.find(id);
  }

  @GetMapping
  InterestRatePage list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.list(page, size);
  }

  @GetMapping("/effective")
  InterestRate findEffectiveOn(@RequestParam LocalDate date) {
    return service.findEffectiveOn(date);
  }

  @PutMapping("/{id}")
  InterestRate replace(@PathVariable UUID id, @Valid @RequestBody InterestRateRequest request) {
    return service.replace(id, request);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
