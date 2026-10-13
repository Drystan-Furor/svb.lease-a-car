package svb.leaseacar.lease;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SecurityPropertiesTests {

  @Test
  void rejectsMissingOrWeakJwtConfiguration() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(null, "lease-demo", "lease-platform"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties("too-short", "lease-demo", "lease-platform"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties("a".repeat(32), " ", "lease-platform"));
  }

  @Test
  void rejectsMissingDuplicateOrInvalidAccounts() {
    String hash = new BCryptPasswordEncoder().encode("test-password");
    AccountsProperties.Account broker = new AccountsProperties.Account("broker", hash);

    assertThatIllegalArgumentException().isThrownBy(() -> new AccountsProperties(null, broker));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new AccountsProperties(broker, new AccountsProperties.Account("broker", hash)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new AccountsProperties(
                    broker, new AccountsProperties.Account("employee", "not-bcrypt")));
  }
}
