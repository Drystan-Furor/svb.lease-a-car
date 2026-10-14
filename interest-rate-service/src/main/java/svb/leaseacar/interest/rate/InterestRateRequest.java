package svb.leaseacar.interest.rate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Input for creating or fully replacing an interest rate. */
record InterestRateRequest(
    @NotNull @Digits(integer = 13, fraction = 6) BigDecimal interestRate,
    @NotNull LocalDate startDate) {}
