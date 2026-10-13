package svb.leaseacar.interest;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validated shared JWT settings loaded from the runtime environment. */
@ConfigurationProperties("lease.jwt")
public record JwtProperties(String secret, String issuer, String audience) {

  public JwtProperties {
    if (secret == null
        || secret.isBlank()
        || secret.getBytes(StandardCharsets.UTF_8).length < 32
        || issuer == null
        || issuer.isBlank()
        || audience == null
        || audience.isBlank()) {
      throw new IllegalArgumentException("JWT configuration is missing or too weak");
    }
  }
}
