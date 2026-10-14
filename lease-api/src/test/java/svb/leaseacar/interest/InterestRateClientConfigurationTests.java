package svb.leaseacar.interest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    properties = {
      "lease.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
      "lease.jwt.issuer=lease-demo",
      "lease.jwt.audience=lease-platform",
      "lease.accounts.broker.username=broker",
      "lease.accounts.broker.password-hash=$2a$10$7EqJtq98hPqEX7fNZaFWoO5vD8M0C6dKj7B6Fynq8A4jCzFwjqVaW",
      "lease.accounts.employee.username=employee",
      "lease.accounts.employee.password-hash=$2a$10$7EqJtq98hPqEX7fNZaFWoO5vD8M0C6dKj7B6Fynq8A4jCzFwjqVaW"
    })
class InterestRateClientConfigurationTests {

  @Autowired private InterestRateClient interestRateClient;
  @Autowired private RestClient.Builder restClientBuilder;

  @Test
  void usesBootManagedRestClientInfrastructure() {
    assertThat(interestRateClient).isNotNull();
    assertThat(restClientBuilder).isNotNull();
  }
}
