package svb.leaseacar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the lease API service. */
@SpringBootApplication(scanBasePackages = "svb.leaseacar")
public class LeaseApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(LeaseApiApplication.class, args);
  }
}
