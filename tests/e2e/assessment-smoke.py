"""Exercise the README contracts against the isolated Docker Compose smoke project."""

import json
import os
import subprocess
import time
import urllib.error
import urllib.request

LEASE_API_PORT = int(os.environ.get("LEASE_API_PORT", "8080"))
INTEREST_RATE_PORT = int(os.environ.get("INTEREST_RATE_PORT", "8081"))
DASHBOARD_PORT = int(os.environ.get("DASHBOARD_PORT", "8082"))


def request(method, path, expected=200, token=None, body=None, port=None):
    port = LEASE_API_PORT if port is None else port
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        f"http://localhost:{port}{path}", data, headers, method=method
    )
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        assert response.status == expected, (method, path, response.status, expected)
    print(f"PASS {method} :{port}{path} -> {expected}", flush=True)
    return json.loads(raw) if raw else None


def ready(port):
    for attempt in range(60):
        try:
            request("GET", "/", 401, port=port)
            return
        except (OSError, AssertionError):
            time.sleep(1)
    raise AssertionError(f"Service on port {port} not ready")


def dashboard_ready():
    for attempt in range(60):
        try:
            with urllib.request.urlopen(
                f"http://localhost:{DASHBOARD_PORT}/", timeout=10
            ) as response:
                page = response.read().decode()
                assert response.status == 200 and "API-testdashboard" in page
                return
        except (OSError, AssertionError):
            time.sleep(1)
    raise AssertionError(f"Dashboard on port {DASHBOARD_PORT} not ready")


def compose(*args):
    subprocess.run(["docker", "compose", *args], check=True)


def login(role):
    result = request("POST", "/auth/login", body={
        "username": role, "password": os.environ[role.upper() + "_PASSWORD"]
    })
    assert result["tokenType"] == "Bearer" and result["expiresIn"] == 900
    return result["accessToken"]


def create_and_update(path, body, token, field, value, port=None):
    created = request("POST", path, 201, token, body, port)
    url = path + "/" + created["id"]
    assert all(created[key] == val for key, val in body.items())
    assert request("GET", url, token=token, port=port) == created
    updated = dict(body, **{field: value})
    result = request("PUT", url, token=token, body=updated, port=port)
    assert all(result[key] == val for key, val in updated.items())
    assert request("GET", url, token=token, port=port) == result
    page = request("GET", path + "?page=0&size=1", token=token, port=port)
    assert len(page["items"]) == 1 and page["total"] > 0
    return created["id"]


for service_port in (LEASE_API_PORT, INTEREST_RATE_PORT):
    ready(service_port)
dashboard_ready()
request("GET", "/lease-api/customers", 401, port=DASHBOARD_PORT)
request("GET", "/interest-api/interest-rates", 401, port=DASHBOARD_PORT)
broker = login("broker")
employee = login("employee")
request("POST", "/auth/login", 401, body={"username": "broker", "password": "wrong"})
assert request("GET", "/customers", token=broker)["total"] == 0
assert request("GET", "/vehicles", token=broker)["total"] == 10329
assert request("GET", "/interest-rates", token=broker, port=INTEREST_RATE_PORT)["total"] == 20

customer = create_and_update("/customers", {
    "name": "Ada Lovelace", "street": "Analytical Engine Road", "houseNumber": "12A",
    "zipcode": "1011 AB", "place": "Amsterdam", "email": "ada@example.test",
    "phoneNumber": "+31 6 12345678"
}, broker, "name", "Ada Byron")
request("GET", "/customers/" + customer, 403, employee)
vehicle_body = {
    "make": "Volvo", "model": "XC40", "version": "Recharge", "numberOfDoors": 5,
    "co2Emission": None, "grossPrice": 70000, "nettPrice": 63000
}
vehicle = create_and_update("/vehicles", vehicle_body, employee, "version", "Demo")
request("POST", "/vehicles", 403, broker, vehicle_body)
seed_vehicle = next(
    item["id"]
    for item in request("GET", "/vehicles?page=0&size=2", token=employee)["items"]
    if item["id"] != vehicle
)
request("DELETE", "/vehicles/" + seed_vehicle, 204, employee)
rate = create_and_update("/interest-rates", {
    "interestRate": 4.4, "startDate": "2030-01-01"
}, employee, "interestRate", 4.5, INTEREST_RATE_PORT)
request("DELETE", "/interest-rates/" + rate, 403, broker, port=INTEREST_RATE_PORT)
effective = request(
    "GET", "/interest-rates/effective?date=2030-01-01",
    token=broker, port=INTEREST_RATE_PORT
)
assert effective["id"] == rate and effective["interestRate"] == 4.5
quote_body = {"vehicleId": vehicle, "mileage": 45000, "duration": 60, "startDate": "2030-01-01"}
quote = request("POST", "/lease-quotes", token=broker, body=quote_body)
assert all(quote[key] == value for key, value in quote_body.items())
assert quote["monthlyRate"] == 239.82 and quote["currency"] == "EUR"
assert quote["interestRate"] == 4.5 and quote["interestRateStartDate"] == "2030-01-01"
request("POST", "/lease-quotes", 403, employee, quote_body)
compose("stop", "interest-rate-service")
try:
    failure = request("POST", "/lease-quotes", 503, broker, quote_body)
    assert failure["status"] == 503 and "monthlyRate" not in failure
finally:
    compose("start", "interest-rate-service")
ready(INTEREST_RATE_PORT)
assert request(
    "GET", "/interest-rates/" + rate, token=broker, port=INTEREST_RATE_PORT
)["interestRate"] == 4.5

# Recreate both application containers while retaining their named volumes.
compose("up", "-d", "--force-recreate", "lease-api", "interest-rate-service")
ready(LEASE_API_PORT)
ready(INTEREST_RATE_PORT)
assert request("GET", "/customers/" + customer, token=broker)["name"] == "Ada Byron"
assert request("GET", "/vehicles/" + vehicle, token=employee)["version"] == "Demo"
request("GET", "/vehicles/" + seed_vehicle, 404, employee)
assert request("GET", "/vehicles", token=employee)["total"] == 10329
assert request(
    "GET", "/interest-rates/" + rate, token=broker, port=INTEREST_RATE_PORT
)["interestRate"] == 4.5
for path, record, token, port in (
    ("/customers", customer, broker, LEASE_API_PORT),
    ("/vehicles", vehicle, employee, LEASE_API_PORT),
    ("/interest-rates", rate, employee, INTEREST_RATE_PORT),
):
    request("DELETE", path + "/" + record, 204, token, port=port)
    request("GET", path + "/" + record, 404, token, port=port)
print("PASS: fixtures, login, CRUD, EUR 239.82 quote, 401, 403, 503 and persistence", flush=True)
