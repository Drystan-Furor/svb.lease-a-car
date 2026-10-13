package svb.leaseacar.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Input for creating or fully replacing a customer. */
public record CustomerRequest(
    @NotBlank String name,
    @NotBlank String street,
    @NotBlank String houseNumber,
    @NotBlank String zipcode,
    @NotBlank String place,
    @NotBlank @Email String email,
    @NotBlank String phoneNumber) {}
