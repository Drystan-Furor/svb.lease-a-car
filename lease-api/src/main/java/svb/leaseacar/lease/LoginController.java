package svb.leaseacar.lease;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Authenticates configured demo accounts and issues short-lived JWT access tokens. */
@RestController
@RequestMapping("/auth")
class LoginController {
  private static final long TOKEN_LIFETIME_SECONDS = 900;
  private final AccountsProperties accounts;
  private final Clock clock;
  private final JwtEncoder encoder;
  private final JwtProperties jwt;
  private final PasswordEncoder passwordEncoder;

  LoginController(
      AccountsProperties accounts,
      Clock clock,
      JwtEncoder encoder,
      JwtProperties jwt,
      PasswordEncoder passwordEncoder) {
    this.accounts = accounts;
    this.clock = clock;
    this.encoder = encoder;
    this.jwt = jwt;
    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping("/login")
  TokenResponse login(@RequestBody LoginRequest request) {
    String role = authenticate(request);
    Instant issuedAt = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(jwt.issuer())
            .audience(List.of(jwt.audience()))
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(TOKEN_LIFETIME_SECONDS))
            .subject(request.username())
            .claim("roles", List.of(role))
            .build();
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new TokenResponse(token, "Bearer", TOKEN_LIFETIME_SECONDS);
  }

  private String authenticate(LoginRequest request) {
    if (request == null || request.username() == null || request.password() == null) {
      throw unauthorized();
    }
    if (matches(accounts.broker(), request)) {
      return "BROKER";
    }
    if (matches(accounts.employee(), request)) {
      return "EMPLOYEE";
    }
    throw unauthorized();
  }

  private boolean matches(AccountsProperties.Account account, LoginRequest request) {
    return account.username().equals(request.username())
        && passwordEncoder.matches(request.password(), account.passwordHash());
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }

  record LoginRequest(String username, String password) {}

  record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
