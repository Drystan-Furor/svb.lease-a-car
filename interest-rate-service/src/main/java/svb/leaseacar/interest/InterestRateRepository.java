package svb.leaseacar.interest;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.springframework.stereotype.Repository;

/** Thread-safe in-memory storage with one interest rate per effective date. */
@Repository
class InterestRateRepository {

  private final Object writeLock = new Object();
  private final ConcurrentHashMap<UUID, InterestRate> rates = new ConcurrentHashMap<>();
  private final NavigableMap<LocalDate, UUID> idsByStartDate = new ConcurrentSkipListMap<>();

  InterestRate create(InterestRateRequest request) {
    synchronized (writeLock) {
      if (idsByStartDate.containsKey(request.startDate())) {
        throw new InterestRateDateConflictException();
      }
      InterestRate rate = new InterestRate(UUID.randomUUID(), request.interestRate(), request.startDate());
      rates.put(rate.id(), rate);
      idsByStartDate.put(rate.startDate(), rate.id());
      return rate;
    }
  }

  Optional<InterestRate> findById(UUID id) {
    return Optional.ofNullable(rates.get(id));
  }

  Optional<InterestRate> replace(UUID id, InterestRateRequest request) {
    synchronized (writeLock) {
      InterestRate existing = rates.get(id);
      if (existing == null) {
        return Optional.empty();
      }
      UUID conflictingId = idsByStartDate.get(request.startDate());
      if (conflictingId != null && !conflictingId.equals(id)) {
        throw new InterestRateDateConflictException();
      }
      if (!existing.startDate().equals(request.startDate())) {
        idsByStartDate.remove(existing.startDate(), id);
        idsByStartDate.put(request.startDate(), id);
      }
      InterestRate replacement =
          new InterestRate(id, request.interestRate(), request.startDate());
      rates.put(id, replacement);
      return Optional.of(replacement);
    }
  }

  boolean delete(UUID id) {
    synchronized (writeLock) {
      InterestRate deleted = rates.remove(id);
      if (deleted == null) {
        return false;
      }
      idsByStartDate.remove(deleted.startDate(), id);
      return true;
    }
  }

  Optional<InterestRate> findEffectiveOn(LocalDate date) {
    synchronized (writeLock) {
      var entry = idsByStartDate.floorEntry(date);
      return entry == null ? Optional.empty() : Optional.ofNullable(rates.get(entry.getValue()));
    }
  }

  InterestRatePage findPage(int page, int size) {
    synchronized (writeLock) {
      List<InterestRate> snapshot =
          rates.values().stream()
              .sorted(Comparator.comparing(rate -> rate.id().toString()))
              .toList();
      long start = (long) page * size;
      if (start >= snapshot.size()) {
        return new InterestRatePage(List.of(), page, size, snapshot.size());
      }
      int fromIndex = (int) start;
      int toIndex = Math.min(fromIndex + size, snapshot.size());
      return new InterestRatePage(snapshot.subList(fromIndex, toIndex), page, size, snapshot.size());
    }
  }
}
