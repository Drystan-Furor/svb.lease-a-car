package svb.leaseacar.lease;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes stateless lease quotes for brokers. */
@RestController
@RequestMapping("/lease-quotes")
@SecurityRequirement(name = "bearerAuth")
class LeaseQuoteController {

  private final LeaseQuoteService service;

  LeaseQuoteController(LeaseQuoteService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Calculate a lease quote")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Calculated lease quote",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = LeaseQuote.class))),
    @ApiResponse(responseCode = "400", description = "Invalid quote input", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Broker role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Vehicle not found", content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "No interest rate is effective on the requested date",
        content = @Content),
    @ApiResponse(
        responseCode = "502",
        description = "Interest-rate service rejected the request or returned an invalid response",
        content = @Content),
    @ApiResponse(
        responseCode = "503", description = "Interest-rate service unavailable", content = @Content)
  })
  LeaseQuote quote(
      @Valid @RequestBody LeaseQuoteRequest request, @AuthenticationPrincipal Jwt principal) {
    return service.quote(request, "Bearer " + principal.getTokenValue());
  }
}
