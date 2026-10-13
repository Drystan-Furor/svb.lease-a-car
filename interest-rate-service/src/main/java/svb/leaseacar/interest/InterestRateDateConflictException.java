package svb.leaseacar.interest;

/** Signals an attempt to assign an already-used effective date to another rate. */
class InterestRateDateConflictException extends RuntimeException {

  InterestRateDateConflictException() {
    super("An interest rate already exists for this startDate");
  }
}
