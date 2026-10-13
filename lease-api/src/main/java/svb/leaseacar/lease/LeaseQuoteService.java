package svb.leaseacar.lease;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import svb.leaseacar.interest.InterestRateClient;
import svb.leaseacar.interest.InterestRateResponse;
import svb.leaseacar.vehicle.Vehicle;
import svb.leaseacar.vehicle.VehicleService;

/** Combines local vehicle data with one historical-interest lookup to calculate a quote. */
@Service
class LeaseQuoteService {

  private final VehicleService vehicleService;
  private final InterestRateClient interestRateClient;
  private final LeaseRateCalculator calculator = new LeaseRateCalculator();

  LeaseQuoteService(VehicleService vehicleService, InterestRateClient interestRateClient) {
    this.vehicleService = vehicleService;
    this.interestRateClient = interestRateClient;
  }

  LeaseQuote quote(LeaseQuoteRequest request, String bearerToken) {
    Vehicle vehicle = vehicleService.find(request.vehicleId());
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
