package svb.leaseacar.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LeaseRateCalculatorTest {

  private final LeaseRateCalculator calculator = new LeaseRateCalculator();

  @Test
  void calculatesMonthlyRateUsingLiteralSourceFormula() {
    BigDecimal monthlyRate = calculator.calculate(45000, 60, new BigDecimal("63000"), new BigDecimal("4.5"));
    BigDecimal lowerInterestRate =
        calculator.calculate(45000, 60, new BigDecimal("63000"), new BigDecimal("4.35"));

    assertEquals(new BigDecimal("239.82"), monthlyRate);
    assertEquals(new BigDecimal("231.95"), lowerInterestRate);
  }

  @Test
  void provesSourceExampleAmountIsNotResultOfLiteralFormula() {
    BigDecimal monthlyRate = calculator.calculate(45000, 60, new BigDecimal("63000"), new BigDecimal("4.5"));

    assertEquals(new BigDecimal("239.82"), monthlyRate);
    assertNotEquals(new BigDecimal("239.76"), monthlyRate);
  }

  @Test
  void allowsNegativeInterestRateWithoutFlooringResult() {
    BigDecimal monthlyRate = calculator.calculate(45000, 60, new BigDecimal("63000"), new BigDecimal("-0.75"));

    assertEquals(new BigDecimal("-35.80"), monthlyRate);
  }

  @Test
  void roundsOnlyFinalResultHalfUpToTwoDecimalPlaces() {
    BigDecimal monthlyRate = calculator.calculate(12, 1, new BigDecimal("200"), BigDecimal.ZERO);
    BigDecimal wholeEuroRate = calculator.calculate(12000, 12, new BigDecimal("12000"), BigDecimal.ZERO);

    assertEquals(new BigDecimal("0.01"), monthlyRate);
    assertEquals(new BigDecimal("1.00"), wholeEuroRate);
  }

  @Test
  void rejectsMissingOrNonPositiveContractInputs() {
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(0, 12, new BigDecimal("12000"), BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(12000, 0, new BigDecimal("12000"), BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(12000, 12, BigDecimal.ZERO, BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(-1, 12, new BigDecimal("12000"), BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(12000, -1, new BigDecimal("12000"), BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(12000, 12, new BigDecimal("-1"), BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(12000, 12, null, BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(12000, 12, new BigDecimal("12000"), null));
  }
}
