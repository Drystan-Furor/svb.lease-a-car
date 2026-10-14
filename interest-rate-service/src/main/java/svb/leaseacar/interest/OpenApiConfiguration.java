package svb.leaseacar.interest;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the interest-rate API description and JWT authorization input for Swagger UI. */
@Configuration
class OpenApiConfiguration {
  static final String BEARER_AUTH = "bearerAuth";

  @Bean
  OpenAPI interestRateApiOpenApi() {
    SecurityScheme bearerScheme =
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT");
    return new OpenAPI()
        .info(
            new Info()
                .title("Interest Rate API")
                .description("Historical interest-rate management and effective-date lookup")
                .version("v1"))
        .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme));
  }
}
