package svb.leaseacar.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class VehicleCsvImporterTests {

  private final VehicleCsvImporter importer = new VehicleCsvImporter();

  @Test
  void importsEveryProvidedVehicleWithoutLosingDuplicates() throws Exception {
    VehicleRepository repository = new VehicleRepository();

    try (var source = new ClassPathResource("vehicles.csv").getInputStream()) {
      importer.importInto(source, repository);
    }

    assertThat(repository.findPage(0, 1).total()).isEqualTo(10_329);
  }

  @Test
  void preservesQuotedAccentedValuesAndDecimalCommasWithoutInventingCo2() {
    VehicleRepository repository = new VehicleRepository();
    String csv =
        """
        make;model;version;#doors;gross_price;nett_price;hp
        "René";"Mégane";"GT; Line";5;25000,50;20000,25;130
        """;

    importer.importInto(cp1252(csv), repository);

    Vehicle vehicle = repository.findPage(0, 1).items().getFirst();
    assertThat(vehicle.make()).isEqualTo("René");
    assertThat(vehicle.model()).isEqualTo("Mégane");
    assertThat(vehicle.version()).isEqualTo("GT; Line");
    assertThat(vehicle.nettPrice()).isEqualByComparingTo("20000.25");
    assertThat(vehicle.co2Emission()).isNull();
  }

  @Test
  void rejectsCorruptRowsAndMissingHeadersWithoutPublishingAnyData() {
    VehicleRepository repository = new VehicleRepository();
    String corrupt =
        """
        make;model;version;#doors;gross_price;nett_price;hp
        Volvo;XC40;Recharge;not-a-number;52000;43000;231
        """;

    assertThatThrownBy(() -> importer.importInto(cp1252(corrupt), repository))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("vehicles.csv row 2");
    assertThat(repository.findPage(0, 50).total()).isZero();

    assertThatThrownBy(
            () ->
                importer.importInto(
                    cp1252("make;model;version;#doors;gross_price;hp\nVolvo;XC40;Recharge;5;1;1\n"),
                    repository))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("vehicles.csv: missing required header nett_price");
    assertThat(repository.findPage(0, 50).total()).isZero();
  }

  @Test
  void doesNotImportAgainWhenRepositoryAlreadyContainsDemoData() {
    VehicleRepository repository = new VehicleRepository();
    String csv =
        "make;model;version;#doors;gross_price;nett_price;hp\nVolvo;XC40;Recharge;5;52000;43000;231\n";

    importer.importInto(cp1252(csv), repository);
    importer.importInto(cp1252(csv), repository);

    assertThat(repository.findPage(0, 50).total()).isEqualTo(1);
  }

  private static ByteArrayInputStream cp1252(String content) {
    return new ByteArrayInputStream(content.getBytes(Charset.forName("windows-1252")));
  }
}
