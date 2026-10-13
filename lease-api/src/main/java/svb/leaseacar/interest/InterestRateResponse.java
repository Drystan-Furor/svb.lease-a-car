package svb.leaseacar.interest;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Downstream representation returned by the interest-rate service. */
public record InterestRateResponse(BigDecimal interestRate, LocalDate startDate) {}
