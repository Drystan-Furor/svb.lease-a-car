package svb.leaseacar.interest;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Coordinates interest-rate CRUD, historical selection, and paging rules. */
@Service
class InterestRateService {

  static final int MAX_PAGE_SIZE = 100;

  private final InterestRateRepository repository;

  InterestRateService(InterestRateRepository repository) {
    this.repository = repository;
  }

  InterestRate create(InterestRateRequest request) {
    return repository.create(request);
  }

  InterestRate find(UUID id) {
    return repository.findById(id).orElseThrow(InterestRateService::notFound);
  }

  InterestRate replace(UUID id, InterestRateRequest request) {
    return repository.replace(id, request).orElseThrow(InterestRateService::notFound);
  }

  void delete(UUID id) {
    if (!repository.delete(id)) {
      throw notFound();
    }
  }

  InterestRate findEffectiveOn(LocalDate date) {
    return repository
        .findEffectiveOn(date)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No interest rate is effective on this date"));
  }

  InterestRatePage list(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "page must be at least 0 and size must be between 1 and 100");
    }
    return repository.findPage(page, size);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Interest rate not found");
  }
}
