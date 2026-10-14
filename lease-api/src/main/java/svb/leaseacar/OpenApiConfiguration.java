package svb.leaseacar;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the lease API description and JWT authorization input for Swagger UI. */
@Configuration
class OpenApiConfiguration {
  static final String BEARER_AUTH = "bearerAuth";

  @Bean
  OpenAPI leaseApiOpenApi() {
    SecurityScheme bearerScheme =
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT");
    return new OpenAPI()
        .info(
            new Info()
                .title("Lease API")
                .description("Customer, vehicle, authentication, and lease quote operations")
                .version("v1"))
        .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme));
  }
}
