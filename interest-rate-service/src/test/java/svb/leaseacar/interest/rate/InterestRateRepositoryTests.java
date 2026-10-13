package svb.leaseacar.interest.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InterestRateRepositoryTests {

  @Test
  void selectsNearestEarlierRateWithoutHttp() {
    InterestRateRepository repository = new InterestRateRepository();
    repository.create(new InterestRateRequest(new BigDecimal("1.25"), LocalDate.of(2014, 8, 14)));
    repository.create(new InterestRateRequest(new BigDecimal("-0.75"), LocalDate.of(2015, 2, 5)));
    repository.create(new InterestRateRequest(BigDecimal.ONE, LocalDate.of(2015, 2, 19)));

    assertThat(repository.findEffectiveOn(LocalDate.of(2014, 8, 13))).isEmpty();
    assertThat(repository.findEffectiveOn(LocalDate.of(2015, 2, 4)))
        .hasValueSatisfying(rate -> assertThat(rate.interestRate()).isEqualByComparingTo("1.25"));
    assertThat(repository.findEffectiveOn(LocalDate.of(2015, 2, 5)))
        .hasValueSatisfying(rate -> assertThat(rate.interestRate()).isEqualByComparingTo("-0.75"));
    assertThat(repository.findEffectiveOn(LocalDate.of(2030, 1, 1)))
        .hasValueSatisfying(rate -> assertThat(rate.interestRate()).isEqualByComparingTo("1"));
  }

  @Test
  void concurrentWritesKeepOneRatePerStartDate() throws Exception {
    InterestRateRepository repository = new InterestRateRepository();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> create =
          () -> {
            ready.countDown();
            start.await();
            try {
              repository.create(new InterestRateRequest(BigDecimal.ONE, LocalDate.of(2022, 1, 1)));
              return true;
            } catch (InterestRateDateConflictException exception) {
              return false;
            }
          };
      Future<Boolean> first = executor.submit(create);
      Future<Boolean> second = executor.submit(create);
      ready.await();
      start.countDown();

      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
      assertThat(repository.findPage(0, 50).total()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }
}
