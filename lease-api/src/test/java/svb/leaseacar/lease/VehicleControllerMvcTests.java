package svb.leaseacar.lease;

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

import java.time.Instant;
import java.util.List;
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
class VehicleControllerMvcTests {

  private static final String VALID_VEHICLE =
      """
      {
        "make": "Volvo",
        "model": "XC40",
        "version": "Recharge",
        "numberOfDoors": 5,
        "co2Emission": null,
        "grossPrice": 52000.00,
        "nettPrice": 43000.00
      }
      """;

  @DynamicPropertySource
  static void accountHashes(DynamicPropertyRegistry registry) {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    registry.add("lease.accounts.broker.password-hash", () -> encoder.encode("test-password"));
    registry.add("lease.accounts.employee.password-hash", () -> encoder.encode("test-password"));
  }

  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private MockMvc mockMvc;

  @Test
  void employeeCanCompleteVehicleCrudCycle() throws Exception {
    String token = token("EMPLOYEE");
    MvcResult created = createVehicle(token, VALID_VEHICLE);
    String location = created.getResponse().getHeader("Location");

    mockMvc
        .perform(get(location).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.make").value("Volvo"))
        .andExpect(jsonPath("$.co2Emission").doesNotExist());

    String updatedVehicle =
        """
        {
          "make": "Volvo",
          "model": "XC40",
          "version": "Single Motor",
          "numberOfDoors": 5,
          "co2Emission": 0,
          "grossPrice": 51000.00,
          "nettPrice": 42000.00
        }
        """;
    mockMvc
        .perform(
            put(location)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedVehicle))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("Single Motor"))
        .andExpect(jsonPath("$.co2Emission").value(0));

    mockMvc
        .perform(delete(location).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(location).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Vehicle not found"));
  }

  @Test
  void rejectsInvalidVehicleFieldsWithProblemDetail() throws Exception {
    String token = token("EMPLOYEE");
    List<String> invalidVehicles =
        List.of(
            VALID_VEHICLE.replace("\"Volvo\"", "\" \""),
            VALID_VEHICLE.replace("\"XC40\"", "\"\""),
            VALID_VEHICLE.replace("\"Recharge\"", "\" \""),
            VALID_VEHICLE.replace("\"numberOfDoors\": 5", "\"numberOfDoors\": 0"),
            VALID_VEHICLE.replace("\"co2Emission\": null", "\"co2Emission\": -1"),
            VALID_VEHICLE.replace("\"grossPrice\": 52000.00", "\"grossPrice\": 0"),
            VALID_VEHICLE.replace("\"nettPrice\": 43000.00", "\"nettPrice\": -1"));

    for (String invalidVehicle : invalidVehicles) {
      mockMvc
          .perform(
              post("/vehicles")
                  .header("Authorization", bearer(token))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidVehicle))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.detail").value("Vehicle input is invalid"));
    }
  }

  @Test
  void rejectsVehicleDecimalsBeyondDatabasePrecisionAndScale() throws Exception {
    String token = token("EMPLOYEE");
    List<String> invalidVehicles =
        List.of(
            VALID_VEHICLE.replace("\"co2Emission\": null", "\"co2Emission\": 1234567890123456.0000"),
            VALID_VEHICLE.replace("\"co2Emission\": null", "\"co2Emission\": 1.00000"),
            VALID_VEHICLE.replace("\"grossPrice\": 52000.00", "\"grossPrice\": 1234567890123456.0000"),
            VALID_VEHICLE.replace("\"grossPrice\": 52000.00", "\"grossPrice\": 1.00000"),
            VALID_VEHICLE.replace("\"nettPrice\": 43000.00", "\"nettPrice\": 1234567890123456.0000"),
            VALID_VEHICLE.replace("\"nettPrice\": 43000.00", "\"nettPrice\": 1.00000"));

    for (String invalidVehicle : invalidVehicles) {
      mockMvc
          .perform(
              post("/vehicles")
                  .header("Authorization", bearer(token))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidVehicle))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.detail").value("Vehicle input is invalid"));
    }
  }

  @Test
  void nullableCo2AndIdenticalVehicleVersionsRemainDistinct() throws Exception {
    String token = token("EMPLOYEE");
    String firstLocation = createVehicle(token, VALID_VEHICLE).getResponse().getHeader("Location");
    String secondLocation = createVehicle(token, VALID_VEHICLE).getResponse().getHeader("Location");

    org.assertj.core.api.Assertions.assertThat(firstLocation).isNotEqualTo(secondLocation);
    mockMvc.perform(get(firstLocation).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc.perform(get(secondLocation).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc.perform(delete(firstLocation).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
    mockMvc.perform(delete(secondLocation).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
  }

  @Test
  void listsAtMostFiftyVehiclesAndValidatesPaging() throws Exception {
    String token = token("EMPLOYEE");
    for (int index = 0; index < 51; index++) {
      createVehicle(token, VALID_VEHICLE.replace("XC40", "XC40-" + index));
    }

    mockMvc
        .perform(get("/vehicles").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(50)))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(50))
        .andExpect(jsonPath("$.total").value(10_380));
    mockMvc
        .perform(get("/vehicles?page=1&size=10").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(10)))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.total").value(10_380));
    mockMvc
        .perform(get("/vehicles?page=-1").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/vehicles?size=0").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/vehicles?size=101").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returnsNotFoundForUnknownVehicleId() throws Exception {
    String token = token("EMPLOYEE");
    String unknown = "/vehicles/123e4567-e89b-12d3-a456-426614174000";

    mockMvc
        .perform(get(unknown).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Vehicle not found"));
    mockMvc
        .perform(
            put(unknown)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_VEHICLE))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(unknown).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsMalformedVehicleIdWithProblemDetail() throws Exception {
    mockMvc
        .perform(
            get("/vehicles/not-a-uuid")
                .header("Authorization", bearer(token("EMPLOYEE"))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Invalid request parameter"));
  }

  @Test
  void rejectsMalformedJsonWithProblemDetail() throws Exception {
    mockMvc
        .perform(
            post("/vehicles")
                .header("Authorization", bearer(token("EMPLOYEE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Malformed request body"));
  }

  private MvcResult createVehicle(String token, String body) throws Exception {
    return mockMvc
        .perform(
            post("/vehicles")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("http://localhost/vehicles/")))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andReturn();
  }

  private String token(String role) {
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
    return jwtEncoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
