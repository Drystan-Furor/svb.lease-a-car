package svb.leaseacar.interest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    properties = {
      "lease.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
      "lease.jwt.issuer=lease-demo",
      "lease.jwt.audience=lease-platform"
    })
@AutoConfigureMockMvc
@ContextConfiguration(
    classes = {InterestRateServiceApplication.class, AuthenticationMvcTests.TestRoutes.class})
class AuthenticationMvcTests {

  private static final String TEST_SECRET =
      "0123456789012345678901234567890123456789012345678901234567890123";

  @Autowired private MockMvc mockMvc;

  @Test
  void rejectsEveryInvalidBearerTokenVariant() throws Exception {
    JwtEncoder trustedEncoder = encoder(TEST_SECRET);
    Instant now = Instant.now();
    List<String> invalidTokens =
        List.of(
            "invalid",
            token(
                trustedEncoder,
                "BROKER",
                now.minusSeconds(180),
                null,
                now.minusSeconds(90),
                "lease-demo",
                "lease-platform"),
            token(
                trustedEncoder,
                "BROKER",
                now,
                now.plusSeconds(120),
                now.plusSeconds(900),
                "lease-demo",
                "lease-platform"),
            token(
                trustedEncoder,
                "BROKER",
                now,
                null,
                now.plusSeconds(900),
                "lease-demo",
                "another-service"),
            token(
                trustedEncoder,
                "BROKER",
                now,
                null,
                now.plusSeconds(900),
                "another-issuer",
                "lease-platform"),
            token(
                encoder("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+-"),
                "BROKER",
                now,
                null,
                now.plusSeconds(900),
                "lease-demo",
                "lease-platform"));

    mockMvc.perform(get("/interest-rates")).andExpect(status().isUnauthorized());
    for (String invalidToken : invalidTokens) {
      mockMvc
          .perform(get("/interest-rates").header("Authorization", bearer(invalidToken)))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void enforcesInterestRateRolePolicy() throws Exception {
    JwtEncoder trustedEncoder = encoder(TEST_SECRET);
    String broker = validToken(trustedEncoder, "BROKER");
    String employee = validToken(trustedEncoder, "EMPLOYEE");

    mockMvc.perform(get("/interest-rates").header("Authorization", bearer(broker)))
        .andExpect(status().isOk());
    mockMvc.perform(get("/interest-rates").header("Authorization", bearer(employee)))
        .andExpect(status().isOk());
    mockMvc.perform(post("/interest-rates").header("Authorization", bearer(broker)))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/interest-rates").header("Authorization", bearer(employee)))
        .andExpect(status().isOk());
  }

  @Test
  void validatesEveryRequestAgain() throws Exception {
    JwtEncoder trustedEncoder = encoder(TEST_SECRET);

    mockMvc
        .perform(
            get("/interest-rates")
                .header("Authorization", bearer(validToken(trustedEncoder, "BROKER"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/interest-rates").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
  }

  private static String validToken(JwtEncoder encoder, String role) {
    Instant now = Instant.now();
    return token(
        encoder, role, now, null, now.plusSeconds(900), "lease-demo", "lease-platform");
  }

  private static String token(
      JwtEncoder encoder,
      String role,
      Instant issuedAt,
      Instant notBefore,
      Instant expiresAt,
      String issuer,
      String audience) {
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject("test-user")
            .claim("roles", List.of(role));
    if (notBefore != null) {
      claims.notBefore(notBefore);
    }
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
        .getTokenValue();
  }

  private static JwtEncoder encoder(String secret) {
    return new NimbusJwtEncoder(
        new ImmutableSecret<>(
            new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  @RestController
  static class TestRoutes {
    @GetMapping("/interest-rates")
    String read() {
      return "reached";
    }

    @PostMapping("/interest-rates")
    String write() {
      return "reached";
    }
  }
}
