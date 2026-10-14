package svb.leaseacar.customer;

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

/** Exposes customer CRUD and bounded paging. */
@RestController
@RequestMapping("/customers")
@SecurityRequirement(name = "bearerAuth")
class CustomerController {

  private final CustomerService service;

  CustomerController(CustomerService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Create a customer")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Customer created",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Customer.class))),
    @ApiResponse(responseCode = "400", description = "Invalid customer input", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Broker role required", content = @Content)
  })
  ResponseEntity<Customer> create(@Valid @RequestBody CustomerRequest request) {
    Customer customer = service.create(request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(customer.id())
            .toUri();
    return ResponseEntity.created(location).body(customer);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a customer")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Customer found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Customer.class))),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Broker role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
  })
  Customer find(@PathVariable UUID id) {
    return service.find(id);
  }

  @GetMapping
  @Operation(summary = "List customers")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Customer page",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CustomerPage.class))),
    @ApiResponse(responseCode = "400", description = "Invalid page request", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Broker role required", content = @Content)
  })
  CustomerPage list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.list(page, size);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Replace a customer")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Customer replaced",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Customer.class))),
    @ApiResponse(responseCode = "400", description = "Invalid customer input", content = @Content),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Broker role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
  })
  Customer replace(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
    return service.replace(id, request);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a customer")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Customer deleted"),
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
    @ApiResponse(responseCode = "403", description = "Broker role required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
  })
  ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
