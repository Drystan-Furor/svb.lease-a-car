package svb.leaseacar.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Input for creating or fully replacing a customer. */
public record CustomerRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String street,
    @NotBlank @Size(max = 50) String houseNumber,
    @NotBlank @Size(max = 20) String zipcode,
    @NotBlank @Size(max = 255) String place,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 50) String phoneNumber) {}
