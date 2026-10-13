package svb.leaseacar.lease;

import java.util.List;

/** One bounded page of vehicle versions. */
public record VehiclePage(List<Vehicle> items, int page, int size, long total) {}
