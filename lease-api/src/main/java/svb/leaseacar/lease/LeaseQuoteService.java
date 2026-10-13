package svb.leaseacar.lease;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Combines local vehicle data with one historical-interest lookup to calculate a quote. */
@Service
class LeaseQuoteService {

  private final VehicleRepository vehicleRepository;
  private final InterestRateClient interestRateClient;
  private final LeaseRateCalculator calculator = new LeaseRateCalculator();

  LeaseQuoteService(VehicleRepository vehicleRepository, InterestRateClient interestRateClient) {
    this.vehicleRepository = vehicleRepository;
    this.interestRateClient = interestRateClient;
  }

  LeaseQuote quote(LeaseQuoteRequest request, String bearerToken) {
    Vehicle vehicle =
        vehicleRepository
            .findById(request.vehicleId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    InterestRateResponse interestRate =
        interestRateClient.findEffectiveOn(request.startDate(), bearerToken);
    BigDecimal monthlyRate =
        calculator.calculate(
            request.mileage(), request.duration(), vehicle.nettPrice(), interestRate.interestRate());
    return new LeaseQuote(
        vehicle.id(),
        request.mileage(),
        request.duration(),
        request.startDate(),
        vehicle.nettPrice(),
        interestRate.interestRate(),
        interestRate.startDate(),
        monthlyRate,
        "EUR");
  }
}
