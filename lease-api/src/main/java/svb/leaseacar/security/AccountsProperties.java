package svb.leaseacar.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validated demo-account configuration loaded from the runtime environment. */
@ConfigurationProperties("lease.accounts")
public record AccountsProperties(Account broker, Account employee) {
  private static final Pattern BCRYPT_PATTERN =
      Pattern.compile("^\\$2[aby]\\$(\\d{2})\\$[./A-Za-z0-9]{53}$");

  public AccountsProperties {
    if (!valid(broker)
        || !valid(employee)
        || broker.username().equals(employee.username())) {
      throw new IllegalArgumentException("Demo account configuration is invalid");
    }
  }

  private static boolean valid(Account account) {
    if (account == null || account.username() == null || account.username().isBlank()) {
      return false;
    }
    Matcher matcher =
        BCRYPT_PATTERN.matcher(account.passwordHash() == null ? "" : account.passwordHash());
    if (!matcher.matches()) {
      return false;
    }
    int strength = Integer.parseInt(matcher.group(1));
    return strength >= 4 && strength <= 31;
  }

  /** One configured account with a BCrypt password hash. */
  public record Account(String username, String passwordHash) {}
}
