package svb.leaseacar.vehicle;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Parses the supplied Windows-1252 vehicle CSV before publishing it to memory. */
class VehicleCsvImporter {

  private static final String FILE_NAME = "vehicles.csv";
  private static final List<String> REQUIRED_HEADERS =
      List.of("make", "model", "version", "#doors", "gross_price", "nett_price");
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

  void importInto(InputStream source, VehicleRepository repository) {
    if (!repository.isEmpty()) {
      return;
    }
    repository.createAllIfEmpty(parse(source));
  }

  private List<VehicleRequest> parse(InputStream source) {
    CSVFormat format =
        CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .get();
    try (CSVParser parser =
        CSVParser.builder()
            .setReader(new InputStreamReader(source, WINDOWS_1252))
            .setFormat(format)
            .get()) {
      requireHeaders(parser);
      return parser.stream().map(this::toRequest).toList();
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

  private VehicleRequest toRequest(CSVRecord record) {
    try {
      return new VehicleRequest(
          required(record, "make"),
          required(record, "model"),
          required(record, "version"),
          Integer.valueOf(required(record, "#doors")),
          null,
          decimal(record, "gross_price"),
          decimal(record, "nett_price"));
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

  private static BigDecimal decimal(CSVRecord record, String header) {
    return new BigDecimal(required(record, header).replace(',', '.'));
  }

  private static IllegalStateException rowError(CSVRecord record, RuntimeException exception) {
    return new IllegalStateException(
        FILE_NAME + " row " + (record.getRecordNumber() + 1) + ": invalid value", exception);
  }
}
