package svb.leaseacar.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import svb.leaseacar.LeaseApiApplication;

class CustomerRepositoryRestartTests {

  @TempDir Path databaseDirectory;

  @Test
  void keepsCreatedAndUpdatedCustomerAcrossApplicationRestarts() {
    UUID customerId;
    try (ConfigurableApplicationContext context = startApplication()) {
      CustomerRepository repository = context.getBean(CustomerRepository.class);
      Customer created = repository.create(request("first@example.test"));
      customerId = created.id();
      repository.replace(customerId, request("updated@example.test"));
    }

    try (ConfigurableApplicationContext context = startApplication()) {
      CustomerRepository repository = context.getBean(CustomerRepository.class);
      assertThat(repository.findById(customerId))
          .hasValueSatisfying(
              customer -> assertThat(customer.email()).isEqualTo("updated@example.test"));
      assertThat(repository.delete(customerId)).isTrue();
    }

    try (ConfigurableApplicationContext context = startApplication()) {
      assertThat(context.getBean(CustomerRepository.class).findById(customerId)).isEmpty();
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

  private static CustomerRequest request(String email) {
    return new CustomerRequest(
        "Ada Lovelace", "Analytical Street", "1", "1234AB", "London", email, "+31000000000");
  }
}
