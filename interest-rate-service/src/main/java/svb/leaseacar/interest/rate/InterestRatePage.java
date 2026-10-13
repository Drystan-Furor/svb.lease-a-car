package svb.leaseacar.interest.rate;

import java.util.List;

/** One bounded page of interest rates. */
public record InterestRatePage(List<InterestRate> items, int page, int size, long total) {}
