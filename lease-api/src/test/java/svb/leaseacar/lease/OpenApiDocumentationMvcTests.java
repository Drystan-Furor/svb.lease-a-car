package svb.leaseacar.lease;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "lease.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
      "lease.jwt.issuer=lease-demo",
      "lease.jwt.audience=lease-platform",
      "lease.accounts.broker.username=broker",
      "lease.accounts.employee.username=employee"
    })
@AutoConfigureMockMvc
class OpenApiDocumentationMvcTests {

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void accountHashes(DynamicPropertyRegistry registry) {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    registry.add("lease.accounts.broker.password-hash", () -> encoder.encode("test-password"));
    registry.add("lease.accounts.employee.password-hash", () -> encoder.encode("test-password"));
  }

  @Test
  void publishesLeaseApiContractWithBearerSecurityAndDtoSchemas() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Lease API"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
        .andExpect(jsonPath("$.paths['/auth/login'].post").exists())
        .andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist())
        .andExpect(jsonPath("$.paths['/customers'].post.requestBody").exists())
        .andExpect(jsonPath("$.paths['/customers'].post.security[0].bearerAuth").isArray())
        .andExpect(
            jsonPath("$.paths['/customers'].post.responses['201'].content['application/json'].schema.$ref")
                .value("#/components/schemas/Customer"))
        .andExpect(jsonPath("$.paths['/customers/{id}'].get.responses['404']").exists())
        .andExpect(jsonPath("$.paths['/vehicles'].post.responses['403']").exists())
        .andExpect(jsonPath("$.paths['/lease-quotes'].post.responses['503']").exists())
        .andExpect(
            jsonPath("$.paths['/lease-quotes'].post.responses['200'].content['application/json'].schema.$ref")
                .value("#/components/schemas/LeaseQuote"))
        .andExpect(
            jsonPath("$.components.schemas.CustomerRequest.required")
                .value(
                    containsInAnyOrder(
                        "name",
                        "street",
                        "houseNumber",
                        "zipcode",
                        "place",
                        "email",
                        "phoneNumber")))
        .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.email.format").value("email"))
        .andExpect(
            jsonPath("$.components.schemas.LeaseQuoteRequest.required")
                .value(containsInAnyOrder("vehicleId", "mileage", "duration", "startDate")))
        .andExpect(jsonPath("$.components.schemas.LeaseQuoteRequest.properties.vehicleId.format").value("uuid"))
        .andExpect(jsonPath("$.components.schemas.LeaseQuoteRequest.properties.startDate.format").value("date"));
  }

  @Test
  void exposesSwaggerUiWithoutWeakeningBusinessEndpointSecurity() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mockMvc.perform(get("/customers")).andExpect(status().isUnauthorized());
  }
}
