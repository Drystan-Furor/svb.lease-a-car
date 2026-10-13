package svb.leaseacar.interest;

import java.time.Duration;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/** Calls the configured interest-rate service once for each accepted quote request. */
@Component
public class InterestRateClient {

  private final RestClient restClient;

  InterestRateClient(
      @Value("${lease.interest-rate-service-url:${INTEREST_RATE_SERVICE_URL:http://localhost:8081}}")
          String baseUrl) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(1));
    requestFactory.setReadTimeout(Duration.ofSeconds(2));
    restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  public InterestRateResponse findEffectiveOn(LocalDate date, String bearerToken) {
    try {
      InterestRateResponse response =
          restClient
              .get()
              .uri(uriBuilder -> uriBuilder.path("/interest-rates/effective").queryParam("date", date).build())
              .header(HttpHeaders.AUTHORIZATION, bearerToken)
              .retrieve()
              .body(InterestRateResponse.class);
      if (response == null || response.interestRate() == null || response.startDate() == null) {
        throw invalidResponse();
      }
      return response;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_CONTENT, "No interest rate is effective on this date");
      }
      throw invalidResponse();
    } catch (ResourceAccessException exception) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Interest-rate service is unavailable");
    } catch (RestClientException exception) {
      throw invalidResponse();
    }
  }

  private static ResponseStatusException invalidResponse() {
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Interest-rate service returned an invalid response");
  }
}
