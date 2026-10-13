package svb.leaseacar.lease;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Downstream representation returned by the interest-rate service. */
record InterestRateResponse(BigDecimal interestRate, LocalDate startDate) {}
