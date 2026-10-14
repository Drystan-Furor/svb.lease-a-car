package svb.leaseacar.vehicle;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

final class VehicleRepositoryTestFactory {

  private VehicleRepositoryTestFactory() {}

  static VehicleRepository create() {
    var dataSource =
        new EmbeddedDatabaseBuilder()
            .generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2)
            .addScript("schema.sql")
            .build();
    return new VehicleRepository(
        JdbcClient.create(dataSource),
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
  }
}
