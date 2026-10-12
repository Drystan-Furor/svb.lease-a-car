package svb.lease_a_car.lease;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Calculates monthly lease rates using the literal formula from the assignment.
 *
 * <p>The calculation is {@code (((mileage / 12) * duration) / nettPrice) +
 * (((interestRate / 100) * nettPrice) / 12)}. Intermediate values use DECIMAL128 and only the
 * final amount is rounded HALF_UP to two decimal places. No correction factor is applied to match
 * the conflicting source example of EUR 239.76; the literal formula produces EUR 239.82.
 */
public final class LeaseRateCalculator {

  private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
  private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

  /**
   * Calculates the monthly lease rate.
   *
   * @param annualMileage kilometres driven per year; must be positive
   * @param durationMonths contract duration in months; must be positive
   * @param nettPrice vehicle net price; must be positive
   * @param interestRate annual percentage rate; may be negative
   * @return monthly rate in EUR, rounded HALF_UP to two decimal places
   * @throws IllegalArgumentException when a required input is missing or non-positive
   */
  public BigDecimal calculate(
      int annualMileage, int durationMonths, BigDecimal nettPrice, BigDecimal interestRate) {
    validate(annualMileage, durationMonths, nettPrice, interestRate);

    BigDecimal mileageComponent = BigDecimal.valueOf(annualMileage)
        .divide(MONTHS_PER_YEAR, MathContext.DECIMAL128)
        .multiply(BigDecimal.valueOf(durationMonths), MathContext.DECIMAL128)
        .divide(nettPrice, MathContext.DECIMAL128);
    BigDecimal interestComponent = interestRate
        .divide(PERCENT, MathContext.DECIMAL128)
        .multiply(nettPrice, MathContext.DECIMAL128)
        .divide(MONTHS_PER_YEAR, MathContext.DECIMAL128);

    return mileageComponent.add(interestComponent, MathContext.DECIMAL128)
        .setScale(2, RoundingMode.HALF_UP);
  }

  private void validate(
      int annualMileage, int durationMonths, BigDecimal nettPrice, BigDecimal interestRate) {
    if (annualMileage <= 0 || durationMonths <= 0 || nettPrice == null
        || nettPrice.signum() <= 0 || interestRate == null) {
      throw new IllegalArgumentException("Mileage, duration, nett price, and interest rate are required.");
    }
  }
}
