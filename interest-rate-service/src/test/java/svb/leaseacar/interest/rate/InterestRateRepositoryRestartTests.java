package svb.leaseacar.interest.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import svb.leaseacar.interest.InterestRateServiceApplication;

class InterestRateRepositoryRestartTests {

  private static final long SEED_COUNT = 20;
  private static final LocalDate USER_RATE_DATE = LocalDate.of(2025, 1, 1);

  @TempDir Path databaseDirectory;

  @Test
  void keepsUserRateAndDoesNotRestoreDeletedRatesAfterRestart() {
    UUID deletedSeedId;
    UUID userRateId;
    try (ConfigurableApplicationContext context = startApplication()) {
      InterestRateRepository repository = context.getBean(InterestRateRepository.class);
      assertThat(repository.findPage(0, 1).total()).isEqualTo(SEED_COUNT);
      deletedSeedId = repository.findPage(0, 1).items().getFirst().id();
      assertThat(repository.delete(deletedSeedId)).isTrue();
      userRateId =
          repository.create(new InterestRateRequest(new BigDecimal("2.75"), USER_RATE_DATE)).id();
    }

    try (ConfigurableApplicationContext context = startApplication()) {
      InterestRateRepository repository = context.getBean(InterestRateRepository.class);
      assertThat(repository.findPage(0, 50).total()).isEqualTo(SEED_COUNT);
      assertThat(repository.findById(userRateId))
          .hasValueSatisfying(
              rate -> assertThat(rate.interestRate()).isEqualByComparingTo("2.75"));
      assertThat(repository.findEffectiveOn(USER_RATE_DATE)).map(InterestRate::id).contains(userRateId);
      assertThatThrownBy(
              () ->
                  repository.create(
                      new InterestRateRequest(new BigDecimal("3.00"), USER_RATE_DATE)))
          .isInstanceOf(InterestRateDateConflictException.class);
      assertThat(repository.delete(userRateId)).isTrue();
    }

    try (ConfigurableApplicationContext context = startApplication()) {
      InterestRateRepository repository = context.getBean(InterestRateRepository.class);
      assertThat(repository.findPage(0, 50).total()).isEqualTo(SEED_COUNT - 1);
      assertThat(repository.findById(deletedSeedId)).isEmpty();
      assertThat(repository.findById(userRateId)).isEmpty();
    }
  }

  private ConfigurableApplicationContext startApplication() {
    String databasePath = databaseDirectory.resolve("interest-rates").toAbsolutePath().toString();
    return new SpringApplicationBuilder(InterestRateServiceApplication.class)
        .profiles("dev")
        .run(
            "--server.port=0",
            "--spring.datasource.url=jdbc:h2:file:"
                + databasePath
                + ";DB_CLOSE_ON_EXIT=FALSE",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=");
  }
}
