package svb.leaseacar.lease;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Calculated quote with its vehicle price, historical interest rate, and supplied contract inputs. */
public record LeaseQuote(
    UUID vehicleId,
    int mileage,
    int duration,
    LocalDate startDate,
    BigDecimal nettPrice,
    BigDecimal interestRate,
    LocalDate interestRateStartDate,
    BigDecimal monthlyRate,
    String currency) {}
