package svb.leaseacar.interest.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** An annual interest rate that becomes effective on a date. */
public record InterestRate(UUID id, BigDecimal interestRate, LocalDate startDate) {}
