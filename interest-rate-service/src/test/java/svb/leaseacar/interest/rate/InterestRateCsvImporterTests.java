package svb.leaseacar.interest.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class InterestRateCsvImporterTests {

  private final InterestRateCsvImporter importer = new InterestRateCsvImporter();

  @Test
  void importsProvidedRatesWithDecimalCommasAndStrictDates() throws Exception {
    InterestRateRepository repository = InterestRateRepositoryTestFactory.create();

    try (var source = new ClassPathResource("interest-rates.csv").getInputStream()) {
      importer.importInto(source, repository);
    }

    assertThat(repository.findPage(0, 50).total()).isEqualTo(20);
    assertThat(repository.findEffectiveOn(LocalDate.of(2015, 2, 5)))
        .hasValueSatisfying(rate -> assertThat(rate.interestRate()).isEqualByComparingTo("-0.75"));
  }

  @Test
  void rejectsInvalidDatesAndMissingHeadersWithoutPublishingAnyData() {
    InterestRateRepository repository = InterestRateRepositoryTestFactory.create();
    String invalidDate = "interestrate;startdate\n-0,75;31-2-2015\n";

    assertThatThrownBy(
            () -> importer.importInto(utf8(invalidDate), repository))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("interest-rates.csv row 2");
    assertThat(repository.findPage(0, 50).total()).isZero();

    assertThatThrownBy(() -> importer.importInto(utf8("interestrate\n0,25\n"), repository))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("interest-rates.csv: missing required header startdate");
    assertThat(repository.findPage(0, 50).total()).isZero();
  }

  @Test
  void doesNotImportAgainWhenRepositoryAlreadyContainsDemoData() {
    InterestRateRepository repository = InterestRateRepositoryTestFactory.create();
    String csv = "interestrate;startdate\n0,25;27-6-2014\n";

    importer.importInto(utf8(csv), repository);
    importer.importInto(utf8(csv), repository);

    assertThat(repository.findPage(0, 50).total()).isEqualTo(1);
  }

  private static ByteArrayInputStream utf8(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
