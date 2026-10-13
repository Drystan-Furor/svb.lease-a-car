package svb.leaseacar.interest;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Input for creating or fully replacing an interest rate. */
record InterestRateRequest(@NotNull BigDecimal interestRate, @NotNull LocalDate startDate) {}
