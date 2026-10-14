package svb.leaseacar.vehicle;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
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

/** Exposes vehicle-version CRUD and bounded paging. */
@RestController
@RequestMapping("/vehicles")
@SecurityRequirement(name = "bearerAuth")
class VehicleController {

  private final VehicleService service;

  VehicleController(VehicleService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Create a vehicle version")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Vehicle created",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Vehicle.class))),
    @ApiResponse(responseCode = "400", description = "Invalid vehicle input", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Employee role required", content = @Content)
  })
  ResponseEntity<Vehicle> create(@Valid @RequestBody VehicleRequest request) {
    Vehicle vehicle = service.create(request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(vehicle.id())
            .toUri();
    return ResponseEntity.created(location).body(vehicle);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a vehicle version")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Vehicle found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Vehicle.class))),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(
        responseCode = "403", description = "Broker or employee role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Vehicle not found", content = @Content)
  })
  Vehicle find(@PathVariable UUID id) {
    return service.find(id);
  }

  @GetMapping
  @Operation(summary = "List vehicle versions")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Vehicle page",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = VehiclePage.class))),
    @ApiResponse(responseCode = "400", description = "Invalid page request", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(
        responseCode = "403", description = "Broker or employee role required", content = @Content)
  })
  VehiclePage list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.list(page, size);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Replace a vehicle version")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Vehicle replaced",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Vehicle.class))),
    @ApiResponse(responseCode = "400", description = "Invalid vehicle input", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Employee role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Vehicle not found", content = @Content)
  })
  Vehicle replace(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
    return service.replace(id, request);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a vehicle version")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Vehicle deleted"),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Employee role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Vehicle not found", content = @Content)
  })
  ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
