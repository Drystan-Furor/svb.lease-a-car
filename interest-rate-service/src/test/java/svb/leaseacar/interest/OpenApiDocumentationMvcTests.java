package svb.leaseacar.interest;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "lease.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
      "lease.jwt.issuer=lease-demo",
      "lease.jwt.audience=lease-platform"
    })
@AutoConfigureMockMvc
class OpenApiDocumentationMvcTests {

  @Autowired private MockMvc mockMvc;

  @Test
  void publishesInterestRateContractWithBearerSecurityAndDtoSchema() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Interest Rate API"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
        .andExpect(jsonPath("$.paths['/interest-rates'].post.responses['201']").exists())
        .andExpect(jsonPath("$.paths['/interest-rates'].post.security[0].bearerAuth").isArray())
        .andExpect(
            jsonPath("$.paths['/interest-rates'].post.responses['201'].content['application/json'].schema.$ref")
                .value("#/components/schemas/InterestRate"))
        .andExpect(jsonPath("$.paths['/interest-rates'].post.responses['400']").exists())
        .andExpect(jsonPath("$.paths['/interest-rates'].post.responses['409']").exists())
        .andExpect(jsonPath("$.paths['/interest-rates/{id}'].get.responses['404']").exists())
        .andExpect(jsonPath("$.paths['/interest-rates/effective'].get.responses['404']").exists())
        .andExpect(
            jsonPath("$.components.schemas.InterestRateRequest.required")
                .value(containsInAnyOrder("interestRate", "startDate")))
        .andExpect(jsonPath("$.components.schemas.InterestRateRequest.properties.startDate.format").value("date"));
  }

  @Test
  void exposesSwaggerUiWithoutWeakeningBusinessEndpointSecurity() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mockMvc.perform(get("/interest-rates")).andExpect(status().isUnauthorized());
  }
}
