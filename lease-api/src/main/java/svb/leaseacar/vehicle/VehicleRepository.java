package svb.leaseacar.vehicle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable H2 storage for vehicle versions. */
@Repository
class VehicleRepository {

  private static final String SEED_NAME = "vehicles-v1";
  private static final RowMapper<Vehicle> ROW_MAPPER = VehicleRepository::mapVehicle;
  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  VehicleRepository(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  Vehicle create(VehicleRequest request) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO vehicles
              (id, make, model, vehicle_version, number_of_doors, co2_emission,
               gross_price, nett_price)
            VALUES
              (:id, :make, :model, :version, :numberOfDoors, :co2Emission,
               :grossPrice, :nettPrice)
            """)
        .param("id", id)
        .param("make", request.make())
        .param("model", request.model())
        .param("version", request.version())
        .param("numberOfDoors", request.numberOfDoors())
        .param("co2Emission", request.co2Emission())
        .param("grossPrice", request.grossPrice())
        .param("nettPrice", request.nettPrice())
        .update();
    return fromRequest(id, request);
  }

  Optional<Vehicle> findById(UUID id) {
    return jdbc.sql("SELECT * FROM vehicles WHERE id = :id")
        .param("id", id)
        .query(ROW_MAPPER)
        .optional();
  }

  boolean isEmpty() {
    return jdbc.sql("SELECT COUNT(*) FROM vehicles").query(Long.class).single() == 0;
  }

  boolean createAllIfEmpty(List<VehicleRequest> requests) {
    return Boolean.TRUE.equals(
        transactions.execute(
            status -> {
              if (seedCompleted()) {
                return false;
              }
              if (!isEmpty()) {
                markSeedCompleted();
                return false;
              }
              requests.forEach(this::create);
              markSeedCompleted();
              return true;
            }));
  }

  Optional<Vehicle> replace(UUID id, VehicleRequest request) {
    int updated =
        jdbc.sql(
                """
                UPDATE vehicles
                SET make = :make,
                    model = :model,
                    vehicle_version = :version,
                    number_of_doors = :numberOfDoors,
                    co2_emission = :co2Emission,
                    gross_price = :grossPrice,
                    nett_price = :nettPrice
                WHERE id = :id
                """)
            .param("id", id)
            .param("make", request.make())
            .param("model", request.model())
            .param("version", request.version())
            .param("numberOfDoors", request.numberOfDoors())
            .param("co2Emission", request.co2Emission())
            .param("grossPrice", request.grossPrice())
            .param("nettPrice", request.nettPrice())
            .update();
    return updated == 0 ? Optional.empty() : Optional.of(fromRequest(id, request));
  }

  boolean delete(UUID id) {
    return jdbc.sql("DELETE FROM vehicles WHERE id = :id").param("id", id).update() != 0;
  }

  VehiclePage findPage(int page, int size) {
    long total = jdbc.sql("SELECT COUNT(*) FROM vehicles").query(Long.class).single();
    long offset = (long) page * size;
    if (offset >= total) {
      return new VehiclePage(List.of(), page, size, total);
    }
    List<Vehicle> items =
        jdbc.sql("SELECT * FROM vehicles ORDER BY id LIMIT :size OFFSET :offset")
            .param("size", size)
            .param("offset", offset)
            .query(ROW_MAPPER)
            .list();
    return new VehiclePage(items, page, size, total);
  }

  private boolean seedCompleted() {
    return jdbc.sql("SELECT COUNT(*) FROM seed_state WHERE seed_name = :seedName")
            .param("seedName", SEED_NAME)
            .query(Long.class)
            .single()
        != 0;
  }

  private void markSeedCompleted() {
    jdbc.sql("INSERT INTO seed_state (seed_name) VALUES (:seedName)")
        .param("seedName", SEED_NAME)
        .update();
  }

  private static Vehicle mapVehicle(ResultSet result, int rowNumber) throws SQLException {
    return new Vehicle(
        result.getObject("id", UUID.class),
        result.getString("make"),
        result.getString("model"),
        result.getString("vehicle_version"),
        result.getInt("number_of_doors"),
        result.getBigDecimal("co2_emission"),
        result.getBigDecimal("gross_price"),
        result.getBigDecimal("nett_price"));
  }

  private static Vehicle fromRequest(UUID id, VehicleRequest request) {
    return new Vehicle(
        id,
        request.make(),
        request.model(),
        request.version(),
        request.numberOfDoors(),
        request.co2Emission(),
        request.grossPrice(),
        request.nettPrice());
  }
}
