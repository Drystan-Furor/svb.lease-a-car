package svb.leaseacar.interest.rate;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

final class InterestRateRepositoryTestFactory {

  private InterestRateRepositoryTestFactory() {}

  static InterestRateRepository create() {
    var dataSource =
        new EmbeddedDatabaseBuilder()
            .generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2)
            .addScript("schema.sql")
            .build();
    return new InterestRateRepository(
        JdbcClient.create(dataSource),
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
  }
}
