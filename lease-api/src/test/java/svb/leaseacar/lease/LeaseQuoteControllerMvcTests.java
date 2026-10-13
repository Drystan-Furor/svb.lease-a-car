package svb.leaseacar.lease;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "lease.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
      "lease.jwt.issuer=lease-demo",
      "lease.jwt.audience=lease-platform",
      "lease.accounts.broker.username=broker",
      "lease.accounts.employee.username=employee"
    })
@AutoConfigureMockMvc
class LeaseQuoteControllerMvcTests {

  private static final String TEST_SECRET =
      "0123456789012345678901234567890123456789012345678901234567890123";
  private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();
  private static final AtomicReference<String> RECEIVED_AUTHORIZATION = new AtomicReference<>();
  private static final AtomicReference<String> RECEIVED_QUERY = new AtomicReference<>();
  private static final AtomicReference<DownstreamMode> DOWNSTREAM_MODE =
      new AtomicReference<>(DownstreamMode.SUCCESS);
  private static final HttpServer INTEREST_RATE_SERVER = startInterestRateServer();

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    registry.add("lease.accounts.broker.password-hash", () -> encoder.encode("test-password"));
    registry.add("lease.accounts.employee.password-hash", () -> encoder.encode("test-password"));
    registry.add(
        "lease.interest-rate-service-url",
        () -> "http://localhost:" + INTEREST_RATE_SERVER.getAddress().getPort());
  }

  @AfterAll
  static void stopInterestRateServer() {
    INTEREST_RATE_SERVER.stop(0);
  }

  @BeforeEach
  void resetDownstream() {
    REQUEST_COUNT.set(0);
    RECEIVED_AUTHORIZATION.set(null);
    RECEIVED_QUERY.set(null);
    DOWNSTREAM_MODE.set(DownstreamMode.SUCCESS);
  }

  @Test
  void brokerReceivesQuoteFromVehiclePriceAndDownstreamInterest() throws Exception {
    String employee = token("EMPLOYEE");
    String broker = token("BROKER");
    String vehicleLocation = createVehicle(employee, new BigDecimal("63000"));
    String vehicleId = vehicleLocation.substring(vehicleLocation.lastIndexOf('/') + 1);

    mockMvc
        .perform(
            post("/lease-quotes")
                .header("Authorization", bearer(broker))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"vehicleId\":\"%s\",\"mileage\":45000,\"duration\":60,\"startDate\":\"2019-08-15\"}"
                        .formatted(vehicleId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vehicleId").value(vehicleId))
        .andExpect(jsonPath("$.mileage").value(45000))
        .andExpect(jsonPath("$.duration").value(60))
        .andExpect(jsonPath("$.startDate").value("2019-08-15"))
        .andExpect(jsonPath("$.nettPrice").value(63000))
        .andExpect(jsonPath("$.monthlyRate").value(231.95))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.interestRate").value(4.35))
        .andExpect(jsonPath("$.interestRateStartDate").value("2019-08-15"));

    org.assertj.core.api.Assertions.assertThat(RECEIVED_QUERY.get()).isEqualTo("date=2019-08-15");
    org.assertj.core.api.Assertions.assertThat(RECEIVED_AUTHORIZATION.get()).startsWith("Bearer ");
  }

  @Test
  void rejectsUnknownVehicleAndInvalidContractWithoutCallingInterestService() throws Exception {
    String broker = token("BROKER");

    quote(broker, "123e4567-e89b-12d3-a456-426614174000", 45000, 60, "2019-08-15")
        .andExpect(status().isNotFound());
    org.assertj.core.api.Assertions.assertThat(REQUEST_COUNT.get()).isZero();

    quote(broker, "123e4567-e89b-12d3-a456-426614174000", 0, 60, "2019-08-15")
        .andExpect(status().isBadRequest());
    org.assertj.core.api.Assertions.assertThat(REQUEST_COUNT.get()).isZero();
  }

  @Test
  void mapsMissingHistoricalRateToUnprocessableContent() throws Exception {
    DOWNSTREAM_MODE.set(DownstreamMode.NOT_FOUND);
    String vehicleId = vehicleId(token("EMPLOYEE"), new BigDecimal("63000"));

    quote(token("BROKER"), vehicleId, 45000, 60, "2014-04-30")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("No interest rate is effective on this date"))
        .andExpect(jsonPath("$.monthlyRate").doesNotExist());
    org.assertj.core.api.Assertions.assertThat(REQUEST_COUNT.get()).isEqualTo(1);
  }

  @Test
  void mapsUnavailableInterestServiceToServiceUnavailable() throws Exception {
    DOWNSTREAM_MODE.set(DownstreamMode.DELAYED);
    String vehicleId = vehicleId(token("EMPLOYEE"), new BigDecimal("63000"));

    quote(token("BROKER"), vehicleId, 45000, 60, "2019-08-15")
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.monthlyRate").doesNotExist());
    org.assertj.core.api.Assertions.assertThat(REQUEST_COUNT.get()).isEqualTo(1);
  }

  @Test
  void mapsRejectedAndMalformedDownstreamResponsesToBadGateway() throws Exception {
    String employee = token("EMPLOYEE");
    String broker = token("BROKER");
    String vehicleId = vehicleId(employee, new BigDecimal("63000"));
    DOWNSTREAM_MODE.set(DownstreamMode.UNAUTHORIZED);

    quote(broker, vehicleId, 45000, 60, "2019-08-15")
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.monthlyRate").doesNotExist());
    DOWNSTREAM_MODE.set(DownstreamMode.MALFORMED);
    quote(broker, vehicleId, 45000, 60, "2019-08-15")
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.monthlyRate").doesNotExist());
    org.assertj.core.api.Assertions.assertThat(REQUEST_COUNT.get()).isEqualTo(2);
  }

  private org.springframework.test.web.servlet.ResultActions quote(
      String token, String vehicleId, int mileage, int duration, String startDate) throws Exception {
    return mockMvc.perform(
        post("/lease-quotes")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"vehicleId\":\"%s\",\"mileage\":%d,\"duration\":%d,\"startDate\":\"%s\"}"
                    .formatted(vehicleId, mileage, duration, startDate)));
  }

  private String vehicleId(String token, BigDecimal nettPrice) throws Exception {
    String location = createVehicle(token, nettPrice);
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private String createVehicle(String token, BigDecimal nettPrice) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/vehicles")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"make\":\"Volvo\",\"model\":\"XC40\",\"version\":\"Recharge\",\"numberOfDoors\":5,\"co2Emission\":null,\"grossPrice\":70000,\"nettPrice\":%s}"
                            .formatted(nettPrice)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", startsWith("http://localhost/vehicles/")))
            .andReturn();
    return result.getResponse().getHeader("Location");
  }

  private static HttpServer startInterestRateServer() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/interest-rates/effective", LeaseQuoteControllerMvcTests::effectiveRate);
      server.start();
      return server;
    } catch (IOException exception) {
      throw new IllegalStateException("Could not start interest-rate test server", exception);
    }
  }

  private static void effectiveRate(HttpExchange exchange) throws IOException {
    REQUEST_COUNT.incrementAndGet();
    RECEIVED_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
    RECEIVED_QUERY.set(exchange.getRequestURI().getQuery());
    if (DOWNSTREAM_MODE.get() == DownstreamMode.DELAYED) {
      try {
        Thread.sleep(2500);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    }
    if (DOWNSTREAM_MODE.get() == DownstreamMode.NOT_FOUND) {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
      return;
    }
    if (DOWNSTREAM_MODE.get() == DownstreamMode.UNAUTHORIZED) {
      exchange.sendResponseHeaders(401, -1);
      exchange.close();
      return;
    }
    byte[] body =
        (DOWNSTREAM_MODE.get() == DownstreamMode.MALFORMED
                ? "{malformed-json"
                : "{\"id\":\"123e4567-e89b-12d3-a456-426614174000\",\"interestRate\":4.35,\"startDate\":\"2019-08-15\"}")
            .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private enum DownstreamMode {
    SUCCESS,
    NOT_FOUND,
    UNAUTHORIZED,
    MALFORMED,
    DELAYED
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
