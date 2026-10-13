package svb.leaseacar.lease;

import java.math.BigDecimal;
import java.util.UUID;

/** Vehicle version available for lease calculations. */
public record Vehicle(
    UUID id,
    String make,
    String model,
    String version,
    int numberOfDoors,
    BigDecimal co2Emission,
    BigDecimal grossPrice,
    BigDecimal nettPrice) {}
