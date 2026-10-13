package svb.leaseacar.interest;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads the supplied demo interest-rate history whenever this in-memory service starts. */
@Component
class InterestRateSeedData implements ApplicationRunner {

  private final InterestRateRepository repository;
  private final InterestRateCsvImporter importer = new InterestRateCsvImporter();

  InterestRateSeedData(InterestRateRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    try (var source = new ClassPathResource("interest-rates.csv").getInputStream()) {
      importer.importInto(source, repository);
    }
  }
}
