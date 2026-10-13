package svb.leaseacar.interest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads the supplied demo interest-rate history whenever this in-memory service starts. */
@Component
class InterestRateSeedData implements ApplicationRunner {

  private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("d-M-uuuu");

  private final InterestRateRepository repository;

  InterestRateSeedData(InterestRateRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new ClassPathResource("interest-rates.csv").getInputStream(), StandardCharsets.UTF_8))) {
      reader
          .lines()
          .skip(1)
          .map(this::toRequest)
          .forEach(repository::create);
    }
  }

  private InterestRateRequest toRequest(String line) {
    String[] columns = line.split(";", -1);
    if (columns.length != 2) {
      throw new IllegalStateException("Invalid interest-rate seed row");
    }
    return new InterestRateRequest(
        new BigDecimal(columns[0].replace(',', '.')), LocalDate.parse(columns[1], CSV_DATE));
  }
}
