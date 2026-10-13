package svb.leaseacar.customer;

import java.util.UUID;

/** Customer whose contact details a broker manages. */
public record Customer(
    UUID id,
    String name,
    String street,
    String houseNumber,
    String zipcode,
    String place,
    String email,
    String phoneNumber) {}
