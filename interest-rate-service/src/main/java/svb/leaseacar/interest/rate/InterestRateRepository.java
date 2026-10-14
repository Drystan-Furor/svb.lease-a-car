package svb.leaseacar.interest.rate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable H2 storage with one interest rate per effective date. */
@Repository
class InterestRateRepository {

  private static final String SEED_NAME = "interest-rates-v1";
  private static final RowMapper<InterestRate> ROW_MAPPER = InterestRateRepository::mapRate;
  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  InterestRateRepository(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  InterestRate create(InterestRateRequest request) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.sql(
              """
              INSERT INTO interest_rates (id, interest_rate, start_date)
              VALUES (:id, :interestRate, :startDate)
              """)
          .param("id", id)
          .param("interestRate", request.interestRate())
          .param("startDate", request.startDate())
          .update();
    } catch (DuplicateKeyException exception) {
      throw new InterestRateDateConflictException();
    }
    return fromRequest(id, request);
  }

  Optional<InterestRate> findById(UUID id) {
    return jdbc.sql("SELECT * FROM interest_rates WHERE id = :id")
        .param("id", id)
        .query(ROW_MAPPER)
        .optional();
  }

  boolean isEmpty() {
    return jdbc.sql("SELECT COUNT(*) FROM interest_rates").query(Long.class).single() == 0;
  }

  boolean createAllIfEmpty(List<InterestRateRequest> requests) {
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

  Optional<InterestRate> replace(UUID id, InterestRateRequest request) {
    try {
      int updated =
          jdbc.sql(
                  """
                  UPDATE interest_rates
                  SET interest_rate = :interestRate, start_date = :startDate
                  WHERE id = :id
                  """)
              .param("id", id)
              .param("interestRate", request.interestRate())
              .param("startDate", request.startDate())
              .update();
      return updated == 0 ? Optional.empty() : Optional.of(fromRequest(id, request));
    } catch (DuplicateKeyException exception) {
      throw new InterestRateDateConflictException();
    }
  }

  boolean delete(UUID id) {
    return jdbc.sql("DELETE FROM interest_rates WHERE id = :id").param("id", id).update() != 0;
  }

  Optional<InterestRate> findEffectiveOn(LocalDate date) {
    return jdbc.sql(
            """
            SELECT * FROM interest_rates
            WHERE start_date <= :date
            ORDER BY start_date DESC
            LIMIT 1
            """)
        .param("date", date)
        .query(ROW_MAPPER)
        .optional();
  }

  InterestRatePage findPage(int page, int size) {
    long total = jdbc.sql("SELECT COUNT(*) FROM interest_rates").query(Long.class).single();
    long offset = (long) page * size;
    if (offset >= total) {
      return new InterestRatePage(List.of(), page, size, total);
    }
    List<InterestRate> items =
        jdbc.sql("SELECT * FROM interest_rates ORDER BY id LIMIT :size OFFSET :offset")
            .param("size", size)
            .param("offset", offset)
            .query(ROW_MAPPER)
            .list();
    return new InterestRatePage(items, page, size, total);
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

  private static InterestRate mapRate(ResultSet result, int rowNumber) throws SQLException {
    return new InterestRate(
        result.getObject("id", UUID.class),
        result.getBigDecimal("interest_rate"),
        result.getObject("start_date", LocalDate.class));
  }

  private static InterestRate fromRequest(UUID id, InterestRateRequest request) {
    return new InterestRate(id, request.interestRate(), request.startDate());
  }
}
