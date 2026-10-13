"""Exercise the README contracts against the isolated Docker Compose smoke project."""

import json
import os
import subprocess
import time
import urllib.error
import urllib.request


def request(method, path, expected=200, token=None, body=None, port=8080):
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
            with urllib.request.urlopen("http://localhost:8082/", timeout=10) as response:
                page = response.read().decode()
                assert response.status == 200 and "API-testdashboard" in page
                return
        except (OSError, AssertionError):
            time.sleep(1)
    raise AssertionError("Dashboard on port 8082 not ready")


def compose(*args):
    subprocess.run(["docker", "compose", *args], check=True)


def login(role):
    result = request("POST", "/auth/login", body={
        "username": role, "password": os.environ[role.upper() + "_PASSWORD"]
    })
    assert result["tokenType"] == "Bearer" and result["expiresIn"] == 900
    return result["accessToken"]


def create_and_update(path, body, token, field, value, port=8080):
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


for service_port in (8080, 8081):
    ready(service_port)
dashboard_ready()
request("GET", "/lease-api/customers", 401, port=8082)
request("GET", "/interest-api/interest-rates", 401, port=8082)
broker = login("broker")
employee = login("employee")
request("POST", "/auth/login", 401, body={"username": "broker", "password": "wrong"})
assert request("GET", "/customers", token=broker)["total"] == 0
assert request("GET", "/vehicles", token=broker)["total"] == 10329
assert request("GET", "/interest-rates", token=broker, port=8081)["total"] == 20

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
rate = create_and_update("/interest-rates", {
    "interestRate": 4.4, "startDate": "2030-01-01"
}, employee, "interestRate", 4.5, 8081)
request("DELETE", "/interest-rates/" + rate, 403, broker, port=8081)
effective = request("GET", "/interest-rates/effective?date=2030-01-01", token=broker, port=8081)
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
ready(8081)
# Restart restores fixtures and discards the temporary rate.
request("GET", "/interest-rates/" + rate, 404, broker, port=8081)
rate = request("POST", "/interest-rates", 201, employee, {
    "interestRate": 4.5, "startDate": "2030-01-01"
}, 8081)["id"]
for path, record, token, port in (
    ("/customers", customer, broker, 8080),
    ("/vehicles", vehicle, employee, 8080),
    ("/interest-rates", rate, employee, 8081),
):
    request("DELETE", path + "/" + record, 204, token, port=port)
    request("GET", path + "/" + record, 404, token, port=port)
print("PASS: fixtures, login, CRUD, EUR 239.82 quote, 401, 403, 503 and reset", flush=True)
