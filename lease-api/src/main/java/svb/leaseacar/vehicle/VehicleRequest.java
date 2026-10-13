package svb.leaseacar.vehicle;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Input for creating or fully replacing a vehicle version. */
record VehicleRequest(
    @NotBlank String make,
    @NotBlank String model,
    @NotBlank String version,
    @NotNull @Positive Integer numberOfDoors,
    @PositiveOrZero BigDecimal co2Emission,
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal grossPrice,
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal nettPrice) {}
