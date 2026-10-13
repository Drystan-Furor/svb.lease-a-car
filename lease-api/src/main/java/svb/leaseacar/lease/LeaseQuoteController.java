package svb.leaseacar.lease;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes stateless lease quotes for brokers. */
@RestController
@RequestMapping("/lease-quotes")
class LeaseQuoteController {

  private final LeaseQuoteService service;

  LeaseQuoteController(LeaseQuoteService service) {
    this.service = service;
  }

  @PostMapping
  LeaseQuote quote(
      @Valid @RequestBody LeaseQuoteRequest request, @AuthenticationPrincipal Jwt principal) {
    return service.quote(request, "Bearer " + principal.getTokenValue());
  }
}
