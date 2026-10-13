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
class CustomerControllerMvcTests {

  private static final String VALID_CUSTOMER =
      """
      {
        "name": "Ada Lovelace",
        "street": "Analytical Engine Road",
        "houseNumber": "12A",
        "zipcode": "1011 AB",
        "place": "Amsterdam",
        "email": "ada@example.test",
        "phoneNumber": "+31 6 12345678"
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
  void brokerCanCompleteCustomerCrudCycle() throws Exception {
    String token = token("BROKER");
    MvcResult created = createCustomer(token, VALID_CUSTOMER);
    String location = created.getResponse().getHeader("Location");

    mockMvc
        .perform(get(location).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ada Lovelace"))
        .andExpect(jsonPath("$.houseNumber").value("12A"))
        .andExpect(jsonPath("$.phoneNumber").value("+31 6 12345678"));

    String updatedCustomer = VALID_CUSTOMER.replace("Ada Lovelace", "Grace Hopper");
    mockMvc
        .perform(
            put(location)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedCustomer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Grace Hopper"));

    mockMvc
        .perform(delete(location).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(location).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Customer not found"));
  }

  @Test
  void rejectsInvalidCustomerFieldsWithoutChangingExistingCustomer() throws Exception {
    String token = token("BROKER");
    String location = createCustomer(token, VALID_CUSTOMER).getResponse().getHeader("Location");
    List<String> invalidCustomers =
        List.of(
            VALID_CUSTOMER.replace("Ada Lovelace", " "),
            VALID_CUSTOMER.replace("Analytical Engine Road", ""),
            VALID_CUSTOMER.replace("12A", " "),
            VALID_CUSTOMER.replace("1011 AB", ""),
            VALID_CUSTOMER.replace("Amsterdam", " "),
            VALID_CUSTOMER.replace("ada@example.test", "not-an-email"),
            VALID_CUSTOMER.replace("+31 6 12345678", ""));

    for (String invalidCustomer : invalidCustomers) {
      mockMvc
          .perform(
              put(location)
                  .header("Authorization", bearer(token))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidCustomer))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.detail").value("Customer input is invalid"));
    }

    mockMvc
        .perform(get(location).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ada Lovelace"));
    mockMvc.perform(delete(location).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
  }

  @Test
  void customerManagementIsForbiddenForEmployeeAndMissingCustomerIsNotFound() throws Exception {
    String employeeToken = token("EMPLOYEE");
    String unknown = "/customers/123e4567-e89b-12d3-a456-426614174000";

    mockMvc
        .perform(
            post("/customers")
                .header("Authorization", bearer(employeeToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CUSTOMER))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get(unknown).header("Authorization", bearer(employeeToken)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get(unknown).header("Authorization", bearer(token("BROKER"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Customer not found"));
  }

  @Test
  void listsCustomersWithBoundedPaging() throws Exception {
    String token = token("BROKER");
    for (int index = 0; index < 51; index++) {
      createCustomer(token, VALID_CUSTOMER.replace("Ada Lovelace", "Customer " + index));
    }

    mockMvc
        .perform(get("/customers").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(50)))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(50))
        .andExpect(jsonPath("$.total").value(51));
    mockMvc
        .perform(get("/customers?page=1&size=10").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(10)))
        .andExpect(jsonPath("$.total").value(51));
    mockMvc
        .perform(get("/customers?page=-1").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/customers?size=101").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
  }

  private MvcResult createCustomer(String token, String body) throws Exception {
    return mockMvc
        .perform(
            post("/customers")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("http://localhost/customers/")))
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
