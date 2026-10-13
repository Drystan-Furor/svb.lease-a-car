package svb.leaseacar.lease;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/** Input for a lease quote. Prices and interest rates are deliberately not client inputs. */
public record LeaseQuoteRequest(
    @NotNull UUID vehicleId,
    @NotNull @Positive Integer mileage,
    @NotNull @Positive Integer duration,
    @NotNull LocalDate startDate) {}
