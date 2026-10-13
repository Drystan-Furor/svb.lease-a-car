package svb.leaseacar.interest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Parses the supplied UTF-8 interest-rate CSV before publishing it to memory. */
class InterestRateCsvImporter {

  private static final String FILE_NAME = "interest-rates.csv";
  private static final List<String> REQUIRED_HEADERS = List.of("interestrate", "startdate");
  private static final DateTimeFormatter CSV_DATE =
      DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT);

  void importInto(InputStream source, InterestRateRepository repository) {
    if (!repository.isEmpty()) {
      return;
    }
    repository.createAllIfEmpty(parse(source));
  }

  private List<InterestRateRequest> parse(InputStream source) {
    CSVFormat format =
        CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .get();
    try (CSVParser parser =
        new CSVParser(new InputStreamReader(source, StandardCharsets.UTF_8), format)) {
      requireHeaders(parser);
      List<InterestRateRequest> requests = parser.stream().map(this::toRequest).toList();
      rejectDuplicateDates(requests);
      return requests;
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read " + FILE_NAME, exception);
    }
  }

  private void requireHeaders(CSVParser parser) {
    for (String header : REQUIRED_HEADERS) {
      if (!parser.getHeaderMap().containsKey(header)) {
        throw new IllegalStateException(FILE_NAME + ": missing required header " + header);
      }
    }
  }

  private InterestRateRequest toRequest(CSVRecord record) {
    try {
      String rate = required(record, "interestrate");
      String startDate = required(record, "startdate");
      return new InterestRateRequest(
          new BigDecimal(rate.replace(',', '.')), LocalDate.parse(startDate, CSV_DATE));
    } catch (RuntimeException exception) {
      throw rowError(record, exception);
    }
  }

  private static String required(CSVRecord record, String header) {
    String value = record.get(header);
    if (value.isBlank()) {
      throw new IllegalArgumentException(header + " is blank");
    }
    return value;
  }

  private static void rejectDuplicateDates(List<InterestRateRequest> requests) {
    Set<LocalDate> dates = new HashSet<>();
    for (InterestRateRequest request : requests) {
      if (!dates.add(request.startDate())) {
        throw new IllegalStateException(FILE_NAME + ": duplicate startdate " + request.startDate());
      }
    }
  }

  private static IllegalStateException rowError(CSVRecord record, RuntimeException exception) {
    return new IllegalStateException(
        FILE_NAME + " row " + (record.getRecordNumber() + 1) + ": invalid value", exception);
  }
}
