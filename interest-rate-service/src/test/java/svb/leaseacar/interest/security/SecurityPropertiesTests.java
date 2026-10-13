package svb.leaseacar.interest.security;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class SecurityPropertiesTests {

  @Test
  void rejectsMissingOrWeakJwtConfiguration() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(null, "lease-demo", "lease-platform"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties("too-short", "lease-demo", "lease-platform"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties("a".repeat(32), "lease-demo", " "));
  }
}
