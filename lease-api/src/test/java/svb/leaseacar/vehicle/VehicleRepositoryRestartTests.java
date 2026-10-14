package svb.leaseacar.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import svb.leaseacar.LeaseApiApplication;

class VehicleRepositoryRestartTests {

  private static final long SEED_COUNT = 10_329;

  @TempDir Path databaseDirectory;

  @Test
  void doesNotRestoreDeletedSeedVehicleAfterApplicationRestart() {
    UUID deletedId;
    try (ConfigurableApplicationContext context = startApplication()) {
      VehicleRepository repository = context.getBean(VehicleRepository.class);
      assertThat(repository.findPage(0, 1).total()).isEqualTo(SEED_COUNT);
      deletedId = repository.findPage(0, 1).items().getFirst().id();
      assertThat(repository.delete(deletedId)).isTrue();
    }

    try (ConfigurableApplicationContext context = startApplication()) {
      VehicleRepository repository = context.getBean(VehicleRepository.class);
      assertThat(repository.findPage(0, 1).total()).isEqualTo(SEED_COUNT - 1);
      assertThat(repository.findById(deletedId)).isEmpty();
    }
  }

  private ConfigurableApplicationContext startApplication() {
    String databasePath = databaseDirectory.resolve("lease-api").toAbsolutePath().toString();
    return new SpringApplicationBuilder(LeaseApiApplication.class)
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
