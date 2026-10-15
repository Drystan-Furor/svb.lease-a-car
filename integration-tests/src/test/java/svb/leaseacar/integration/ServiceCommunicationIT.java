package svb.leaseacar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

class ServiceCommunicationIT {

  private static final String JWT_SECRET =
      "0123456789012345678901234567890123456789012345678901234567890123";
  private static final String PASSWORD = "password";
  private static final String PASSWORD_HASH = BCrypt.hashpw(PASSWORD, BCrypt.gensalt());
  private static final Path REPOSITORY_ROOT =
      Path.of(System.getProperty("basedir"))
          .getParent()
          .toAbsolutePath()
          .normalize();
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private static final Network NETWORK = Network.newNetwork();
  private static final GenericContainer<?> INTEREST_RATE_SERVICE = interestRateService();
  private static final GenericContainer<?> LEASE_API = leaseApi();

  @BeforeAll
  static void startServices() {
    INTEREST_RATE_SERVICE.start();
    LEASE_API.start();
  }

  @AfterAll
  static void stopServices() {
    LEASE_API.stop();
    INTEREST_RATE_SERVICE.stop();
    NETWORK.close();
  }

  @Test
  void leaseApiUsesInterestRateServiceToCalculateQuote() throws Exception {
    String employeeToken = login("employee");
    String brokerToken = login("broker");

    Response vehicle =
        post(
            "/vehicles",
            """
            {
              "make": "Volvo",
              "model": "XC40",
              "version": "Recharge",
              "numberOfDoors": 5,
              "co2Emission": null,
              "grossPrice": 70000,
              "nettPrice": 63000
            }
            """,
            employeeToken);
    assertThat(vehicle.statusCode()).isEqualTo(201);
    String vehicleId = JsonPath.read(vehicle.body(), "$.id");

    Response quote =
        post(
            "/lease-quotes",
            """
            {
              "vehicleId": "%s",
              "mileage": 45000,
              "duration": 60,
              "startDate": "2019-08-15"
            }
            """
                .formatted(vehicleId),
            brokerToken);

    assertThat(quote.statusCode()).isEqualTo(200);
    assertThat((Number) JsonPath.read(quote.body(), "$.interestRate"))
        .extracting(Number::doubleValue)
        .isEqualTo(4.35);
    assertThat((Number) JsonPath.read(quote.body(), "$.monthlyRate"))
        .extracting(Number::doubleValue)
        .isEqualTo(231.95);
    assertThat(JsonPath.<String>read(quote.body(), "$.interestRateStartDate"))
        .isEqualTo("2019-08-15");
  }

  private static String login(String username) throws Exception {
    Response response =
        post(
            "/auth/login",
            """
            {"username":"%s","password":"%s"}
            """
                .formatted(username, PASSWORD),
            null);
    assertThat(response.statusCode()).isEqualTo(200);
    return JsonPath.read(response.body(), "$.accessToken");
  }

  private static Response post(String path, String body, String token)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(leaseApiUrl() + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    Optional.ofNullable(token).ifPresent(value -> request.header("Authorization", "Bearer " + value));
    HttpResponse<String> response =
        HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    return new Response(response.statusCode(), response.body());
  }

  private static String leaseApiUrl() {
    return "http://" + LEASE_API.getHost() + ":" + LEASE_API.getMappedPort(8080);
  }

  private static GenericContainer<?> interestRateService() {
    return new GenericContainer<>(serviceImage("interest-rate-service"))
        .withNetwork(NETWORK)
        .withNetworkAliases("interest-rate-service")
        .withEnv("DATABASE_URL", "jdbc:h2:mem:interest-rates;DB_CLOSE_DELAY=-1")
        .withEnv("JWT_SECRET", JWT_SECRET)
        .withExposedPorts(8080)
        .waitingFor(Wait.forHttp("/v3/api-docs").forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(2));
  }

  private static GenericContainer<?> leaseApi() {
    return new GenericContainer<>(serviceImage("lease-api"))
        .withNetwork(NETWORK)
        .withEnv("DATABASE_URL", "jdbc:h2:mem:lease-api;DB_CLOSE_DELAY=-1")
        .withEnv("JWT_SECRET", JWT_SECRET)
        .withEnv("BROKER_USERNAME", "broker")
        .withEnv("BROKER_PASSWORD_HASH", PASSWORD_HASH)
        .withEnv("EMPLOYEE_USERNAME", "employee")
        .withEnv("EMPLOYEE_PASSWORD_HASH", PASSWORD_HASH)
        .withEnv("INTEREST_RATE_SERVICE_URL", "http://interest-rate-service:8080")
        .withExposedPorts(8080)
        .waitingFor(Wait.forHttp("/v3/api-docs").forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(2));
  }

  private static ImageFromDockerfile serviceImage(String target) {
    return new ImageFromDockerfile("lease-a-car-test/" + target, true)
        .withDockerfile(REPOSITORY_ROOT.resolve("Dockerfile"))
        .withTarget(target);
  }

  private record Response(int statusCode, String body) {}
}
