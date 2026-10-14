package svb.leaseacar.interest.rate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Exposes interest-rate CRUD and historical selection. */
@RestController
@RequestMapping("/interest-rates")
@SecurityRequirement(name = "bearerAuth")
class InterestRateController {

  private final InterestRateService service;

  InterestRateController(InterestRateService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Create an interest rate")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Interest rate created",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = InterestRate.class))),
    @ApiResponse(
        responseCode = "400", description = "Invalid interest-rate input", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Employee role required", content = @Content),
    @ApiResponse(responseCode = "409", description = "Start date already exists", content = @Content)
  })
  ResponseEntity<InterestRate> create(@Valid @RequestBody InterestRateRequest request) {
    InterestRate rate = service.create(request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(rate.id())
            .toUri();
    return ResponseEntity.created(location).body(rate);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get an interest rate")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Interest rate found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = InterestRate.class))),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(
        responseCode = "403", description = "Broker or employee role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Interest rate not found", content = @Content)
  })
  InterestRate find(@PathVariable UUID id) {
    return service.find(id);
  }

  @GetMapping
  @Operation(summary = "List interest rates")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Interest-rate page",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = InterestRatePage.class))),
    @ApiResponse(responseCode = "400", description = "Invalid page request", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(
        responseCode = "403", description = "Broker or employee role required", content = @Content)
  })
  InterestRatePage list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.list(page, size);
  }

  @GetMapping("/effective")
  @Operation(summary = "Get the interest rate effective on a date")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Effective interest rate",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = InterestRate.class))),
    @ApiResponse(responseCode = "400", description = "Invalid or missing date", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(
        responseCode = "403", description = "Broker or employee role required", content = @Content),
    @ApiResponse(
        responseCode = "404", description = "No effective interest rate found", content = @Content)
  })
  InterestRate findEffectiveOn(@RequestParam LocalDate date) {
    return service.findEffectiveOn(date);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Replace an interest rate")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Interest rate replaced",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = InterestRate.class))),
    @ApiResponse(
        responseCode = "400", description = "Invalid interest-rate input", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Employee role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Interest rate not found", content = @Content),
    @ApiResponse(responseCode = "409", description = "Start date already exists", content = @Content)
  })
  InterestRate replace(@PathVariable UUID id, @Valid @RequestBody InterestRateRequest request) {
    return service.replace(id, request);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete an interest rate")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Interest rate deleted"),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Employee role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Interest rate not found", content = @Content)
  })
  ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
