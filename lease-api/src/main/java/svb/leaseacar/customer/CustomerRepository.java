package svb.leaseacar.customer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Durable H2 storage for customers. */
@Repository
class CustomerRepository {

  private static final RowMapper<Customer> ROW_MAPPER = CustomerRepository::mapCustomer;
  private final JdbcClient jdbc;

  CustomerRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Customer create(CustomerRequest request) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO customers
              (id, name, street, house_number, zipcode, place, email, phone_number)
            VALUES
              (:id, :name, :street, :houseNumber, :zipcode, :place, :email, :phoneNumber)
            """)
        .param("id", id)
        .param("name", request.name())
        .param("street", request.street())
        .param("houseNumber", request.houseNumber())
        .param("zipcode", request.zipcode())
        .param("place", request.place())
        .param("email", request.email())
        .param("phoneNumber", request.phoneNumber())
        .update();
    return fromRequest(id, request);
  }

  Optional<Customer> findById(UUID id) {
    return jdbc.sql("SELECT * FROM customers WHERE id = :id")
        .param("id", id)
        .query(ROW_MAPPER)
        .optional();
  }

  Optional<Customer> replace(UUID id, CustomerRequest request) {
    int updated =
        jdbc.sql(
                """
                UPDATE customers
                SET name = :name,
                    street = :street,
                    house_number = :houseNumber,
                    zipcode = :zipcode,
                    place = :place,
                    email = :email,
                    phone_number = :phoneNumber
                WHERE id = :id
                """)
            .param("id", id)
            .param("name", request.name())
            .param("street", request.street())
            .param("houseNumber", request.houseNumber())
            .param("zipcode", request.zipcode())
            .param("place", request.place())
            .param("email", request.email())
            .param("phoneNumber", request.phoneNumber())
            .update();
    return updated == 0 ? Optional.empty() : Optional.of(fromRequest(id, request));
  }

  boolean delete(UUID id) {
    return jdbc.sql("DELETE FROM customers WHERE id = :id").param("id", id).update() != 0;
  }

  CustomerPage findPage(int page, int size) {
    long total = jdbc.sql("SELECT COUNT(*) FROM customers").query(Long.class).single();
    long offset = (long) page * size;
    if (offset >= total) {
      return new CustomerPage(List.of(), page, size, total);
    }
    List<Customer> items =
        jdbc.sql("SELECT * FROM customers ORDER BY id LIMIT :size OFFSET :offset")
            .param("size", size)
            .param("offset", offset)
            .query(ROW_MAPPER)
            .list();
    return new CustomerPage(items, page, size, total);
  }

  private static Customer mapCustomer(ResultSet result, int rowNumber) throws SQLException {
    return new Customer(
        result.getObject("id", UUID.class),
        result.getString("name"),
        result.getString("street"),
        result.getString("house_number"),
        result.getString("zipcode"),
        result.getString("place"),
        result.getString("email"),
        result.getString("phone_number"));
  }

  private static Customer fromRequest(UUID id, CustomerRequest request) {
    return new Customer(
        id,
        request.name(),
        request.street(),
        request.houseNumber(),
        request.zipcode(),
        request.place(),
        request.email(),
        request.phoneNumber());
  }
}
