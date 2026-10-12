package svb.lease_a_car;

import org.springframework.boot.SpringApplication;

public class TestLeaseACarApplication {

	public static void main(String[] args) {
		SpringApplication.from(LeaseACarApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
