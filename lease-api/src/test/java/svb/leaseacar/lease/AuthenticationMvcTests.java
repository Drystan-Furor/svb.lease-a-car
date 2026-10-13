package svb.leaseacar.lease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import svb.leaseacar.LeaseApiApplication;

@SpringBootTest(
    properties = {
      "lease.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
      "lease.jwt.issuer=lease-demo",
      "lease.jwt.audience=lease-platform",
      "lease.accounts.broker.username=broker",
      "lease.accounts.employee.username=employee"
    })
@AutoConfigureMockMvc
@ContextConfiguration(
    classes = {
      LeaseApiApplication.class,
      AuthenticationMvcTests.FixedClockConfiguration.class
    })
class AuthenticationMvcTests {

  private static final Instant LOGIN_TIME = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  private static final String PASSWORD = "test-password";
  private static final String TEST_SECRET =
      "0123456789012345678901234567890123456789012345678901234567890123";

  @DynamicPropertySource
  static void accountHashes(DynamicPropertyRegistry registry) {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    registry.add("lease.accounts.broker.password-hash", () -> encoder.encode(PASSWORD));
    registry.add("lease.accounts.employee.password-hash", () -> encoder.encode(PASSWORD));
  }

  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private MockMvc mockMvc;

  @Test
  void loginReturnsExpectedBearerTokenForValidBrokerCredentials() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"broker\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    String marker = "\"accessToken\":\"";
    int tokenStart = responseBody.indexOf(marker) + marker.length();
    String accessToken = responseBody.substring(tokenStart, responseBody.indexOf('"', tokenStart));
    Jwt jwt = jwtDecoder.decode(accessToken);

    assertThat(jwt.getClaimAsString("iss")).isEqualTo("lease-demo");
    assertThat(jwt.getAudience()).containsExactly("lease-platform");
    assertThat(jwt.getSubject()).isEqualTo("broker");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("BROKER");
    assertThat(jwt.getIssuedAt()).isEqualTo(LOGIN_TIME);
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofSeconds(900));
  }

  @Test
  void rejectsIncorrectOrIncompleteCredentials() throws Exception {
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"broker\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsEveryInvalidBearerTokenVariant() throws Exception {
    Instant now = Instant.now();
    List<String> invalidTokens =
        List.of(
            "invalid",
            token(
                jwtEncoder,
                "BROKER",
                now.minusSeconds(180),
                null,
                now.minusSeconds(90),
                "lease-demo",
                "lease-platform"),
            token(
                jwtEncoder,
                "BROKER",
                now,
                now.plusSeconds(120),
                now.plusSeconds(900),
                "lease-demo",
                "lease-platform"),
            token(
                jwtEncoder,
                "BROKER",
                now,
                null,
                now.plusSeconds(900),
                "lease-demo",
                "another-service"),
            token(
                jwtEncoder,
                "BROKER",
                now,
                null,
                now.plusSeconds(900),
                "another-issuer",
                "lease-platform"),
            token(
                forgedEncoder(),
                "BROKER",
                now,
                null,
                now.plusSeconds(900),
                "lease-demo",
                "lease-platform"));

    mockMvc.perform(get("/customers")).andExpect(status().isUnauthorized());
    for (String invalidToken : invalidTokens) {
      mockMvc
          .perform(get("/customers").header("Authorization", bearer(invalidToken)))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void enforcesLeaseApiRolePolicy() throws Exception {
    String broker = validToken("BROKER");
    String employee = validToken("EMPLOYEE");

    mockMvc.perform(get("/customers").header("Authorization", bearer(broker)))
        .andExpect(status().isOk());
    mockMvc.perform(get("/customers").header("Authorization", bearer(employee)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/lease-quotes")
                .header("Authorization", bearer(broker))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/lease-quotes")
                .header("Authorization", bearer(employee))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/vehicles").header("Authorization", bearer(broker)))
        .andExpect(status().isOk());
    mockMvc.perform(get("/vehicles").header("Authorization", bearer(employee)))
        .andExpect(status().isOk());
    mockMvc.perform(post("/vehicles").header("Authorization", bearer(broker)))
        .andExpect(status().isForbidden());
    MvcResult created =
        mockMvc
            .perform(
                post("/vehicles")
                    .header("Authorization", bearer(employee))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "make": "Volvo",
                          "model": "XC40",
                          "version": "Recharge",
                          "numberOfDoors": 5,
                          "co2Emission": null,
                          "grossPrice": 52000,
                          "nettPrice": 43000
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String location = created.getResponse().getHeader("Location");
    mockMvc.perform(get(location).header("Authorization", bearer(broker)))
        .andExpect(status().isOk());
    mockMvc.perform(delete(location).header("Authorization", bearer(broker)))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete(location).header("Authorization", bearer(employee)))
        .andExpect(status().isNoContent());
  }

  @Test
  void authenticatesEveryRequestWithoutCreatingLoginSession() throws Exception {
    String valid = validToken("BROKER");

    mockMvc.perform(get("/customers").header("Authorization", bearer(valid)))
        .andExpect(status().isOk());
    mockMvc.perform(get("/customers").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/customers")).andExpect(status().isUnauthorized());
  }

  @Test
  void validTokenWithoutRoleIsForbiddenInsteadOfFailing() throws Exception {
    Instant now = Instant.now();
    String token =
        token(jwtEncoder, null, now, null, now.plusSeconds(900), "lease-demo", "lease-platform");

    mockMvc.perform(get("/customers").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
  }

  private String validToken(String role) {
    Instant now = Instant.now();
    return token(
        jwtEncoder, role, now, null, now.plusSeconds(900), "lease-demo", "lease-platform");
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
            .subject("test-user");
    if (notBefore != null) {
      claims.notBefore(notBefore);
    }
    if (role != null) {
      claims.claim("roles", List.of(role));
    }
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
        .getTokenValue();
  }

  private static JwtEncoder forgedEncoder() {
    byte[] secret =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+-"
            .getBytes(StandardCharsets.UTF_8);
    return new NimbusJwtEncoder(
        new ImmutableSecret<>(new SecretKeySpec(secret, "HmacSHA256")));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(LOGIN_TIME, ZoneOffset.UTC);
    }
  }

}
