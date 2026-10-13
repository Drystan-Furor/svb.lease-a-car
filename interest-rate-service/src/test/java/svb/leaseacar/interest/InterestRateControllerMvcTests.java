package svb.leaseacar.interest;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    properties = {
      "lease.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
      "lease.jwt.issuer=lease-demo",
      "lease.jwt.audience=lease-platform"
    })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class InterestRateControllerMvcTests {

  private static final String TEST_SECRET =
      "0123456789012345678901234567890123456789012345678901234567890123";

  @Autowired private MockMvc mockMvc;

  @Test
  void selectsRateEffectiveOnRequestedDate() throws Exception {
    mockMvc
        .perform(
            get("/interest-rates/effective?date=2015-02-04")
                .header("Authorization", bearer(token("BROKER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interestRate").value(1.25))
        .andExpect(jsonPath("$.startDate").value("2014-08-14"));
  }

  @Test
  void selectsHistoricalRateAtEveryDateBoundary() throws Exception {
    String token = token("BROKER");

    mockMvc
        .perform(
            get("/interest-rates/effective?date=2015-02-05")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interestRate").value(-0.75))
        .andExpect(jsonPath("$.startDate").value("2015-02-05"));
    mockMvc
        .perform(
            get("/interest-rates/effective?date=2015-02-19")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interestRate").value(1))
        .andExpect(jsonPath("$.startDate").value("2015-02-19"));
    mockMvc
        .perform(
            get("/interest-rates/effective?date=2014-04-30")
                .header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/interest-rates/effective?date=2019-08-15")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interestRate").value(4.35));
    mockMvc
        .perform(
            get("/interest-rates/effective?date=2030-01-01")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interestRate").value(4.35));
  }

  @Test
  void employeeCanManageInterestRatesAndBrokerCanOnlyRead() throws Exception {
    String employee = token("EMPLOYEE");
    String broker = token("BROKER");
    String request = request(1.5, "2022-01-01");

    mockMvc
        .perform(
            post("/interest-rates")
                .header("Authorization", bearer(broker))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isForbidden());
    MvcResult created = create(employee, request);
    String location = created.getResponse().getHeader("Location");

    mockMvc
        .perform(get(location).header("Authorization", bearer(broker)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interestRate").value(1.5));
    mockMvc
        .perform(
            put(location)
                .header("Authorization", bearer(employee))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(-0.5, "2022-01-01")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interestRate").value(-0.5));
    mockMvc
        .perform(delete(location).header("Authorization", bearer(broker)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete(location).header("Authorization", bearer(employee)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(location).header("Authorization", bearer(employee)))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsDuplicateStartDatesWithoutChangingExistingRates() throws Exception {
    String token = token("EMPLOYEE");
    MvcResult first = create(token, request(1, "2022-02-01"));
    String firstLocation = first.getResponse().getHeader("Location");
    MvcResult second = create(token, request(2, "2022-03-01"));
    String secondLocation = second.getResponse().getHeader("Location");

    mockMvc
        .perform(
            post("/interest-rates")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(3, "2022-02-01")))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            put(secondLocation)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(4, "2022-02-01")))
        .andExpect(status().isConflict());
    mockMvc
        .perform(get(firstLocation).header("Authorization", bearer(token)))
        .andExpect(jsonPath("$.interestRate").value(1));
    mockMvc
        .perform(get(secondLocation).header("Authorization", bearer(token)))
        .andExpect(jsonPath("$.interestRate").value(2))
        .andExpect(jsonPath("$.startDate").value("2022-03-01"));
  }

  @Test
  void pagesRatesAndRejectsInvalidInput() throws Exception {
    String token = token("EMPLOYEE");
    for (int index = 0; index < 31; index++) {
      create(token, request(index, LocalDate.of(2022, 4, 1).plusDays(index).toString()));
    }

    mockMvc
        .perform(get("/interest-rates").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(50)))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(50))
        .andExpect(jsonPath("$.total").value(51));
    mockMvc
        .perform(get("/interest-rates?page=1&size=10").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(10)))
        .andExpect(jsonPath("$.total").value(51));
    mockMvc
        .perform(get("/interest-rates?page=-1").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/interest-rates?size=101").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/interest-rates")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2023-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mockMvc
        .perform(
            get("/interest-rates/effective?date=not-a-date")
                .header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/interest-rates/effective").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
  }

  private MvcResult create(String token, String request) throws Exception {
    return mockMvc
        .perform(
            post("/interest-rates")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("http://localhost/interest-rates/")))
        .andReturn();
  }

  private static String request(double interestRate, String startDate) {
    return "{\"interestRate\":%s,\"startDate\":\"%s\"}".formatted(interestRate, startDate);
  }

  private static String token(String role) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("lease-demo")
            .audience(List.of("lease-platform"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .subject("test-user")
            .claim("roles", List.of(role))
            .build();
    JwtEncoder encoder =
        new NimbusJwtEncoder(
            new ImmutableSecret<>(
                new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    return encoder
        .encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
