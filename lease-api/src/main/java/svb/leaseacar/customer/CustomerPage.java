package svb.leaseacar.customer;

import java.util.List;

/** One bounded page of customers. */
public record CustomerPage(List<Customer> items, int page, int size, long total) {}
