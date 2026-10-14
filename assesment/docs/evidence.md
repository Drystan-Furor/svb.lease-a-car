## Assessment handover and evidence

```sh
git clone https://github.com/Drystan-Furor/svb.lease-a-car.git
cd svb.lease-a-car
git checkout 37fb9a00e4b8bfd66edcab3df120cedd18202610
sh tests/e2e/docker-smoke.sh
```

The checkout above selects the delivery SHA verified on both environments. The script prints that
SHA, working-tree changes, host OS/CPU, Docker client/server versions, Compose version, build
results, and functional assertions. A delivery run must start with an empty working-tree status.
The Dockerfile copies only committed source inputs and performs Maven `verify` inside Java 21.
Both module builds run unit/integration tests and Google Java Style checks before producing JARs.
Javadoc describes public request/response contracts, validation, seed import rules, historical
selection, and the calculator's precision and rounding.

| Evidence                                                       | Status                                                                                                                                                                                                                                                                    |
|----------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Local Docker demo                                              | 2026-10-13: Darwin arm64, Docker Desktop 4.94.0, Engine 29.8.2 linux/arm64, Compose 5.5.1; 50 tests, zero style violations, CRUD/quote/401/403/503/reset passed                                                                                                           |
| Verified delivery commit                                       | `37fb9a00e4b8bfd66edcab3df120cedd18202610`; clean worktree on both hosts. Subsequent commits record evidence only.                                                                                                                                                        |
| Local evidence                                                 | [Recorded output](tests/evidence/s10-local.log)                                                                                                                                                                                                                           |
| Second-host OS/CPU and Docker/Compose versions                 | Ubuntu 24.04, Linux x86_64; Docker client/server 28.0.4 linux/amd64; Compose 2.38.2                                                                                                                                                                                       |
| Second-host build/tests/style and CRUD/quote/401/403/503/reset | Passed: 50 tests, zero failures/errors/skips, zero style violations; full demo passed twice across container recreation. [Successful run](https://github.com/Drystan-Furor/svb.lease-a-car/actions/runs/38394349519) and [recorded output](tests/evidence/s10-github.log) |
| Remote publication and assessor access                         | Delivery commit pushed to origin and checked out by Actions. User confirmed they are the assessor; their ADMIN access verified. Public visibility will be changed separately by the owner.                                                                                |

### Known limits

Each service uses one local H2 file database and is not intended for concurrent multi-instance
deployment. Compose data persists until `docker compose down -v`; no HTTP reset exists. Quote
results are not persisted. Authentication uses two configured demo accounts and a shared signing
secret; there is no account administration, refresh-token or revocation flow. Swagger and durable
storage are implemented bonus stories.
The literal formula yields EUR 239.82 for the assignment inputs, as documented above.
