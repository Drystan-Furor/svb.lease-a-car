package svb.leaseacar.lease;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads supplied vehicle data when the in-memory repository starts empty. */
@Component
class VehicleSeedData implements ApplicationRunner {

  private final VehicleRepository repository;
  private final VehicleCsvImporter importer = new VehicleCsvImporter();

  VehicleSeedData(VehicleRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    try (var source = new ClassPathResource("vehicles.csv").getInputStream()) {
      importer.importInto(source, repository);
    }
  }
}
