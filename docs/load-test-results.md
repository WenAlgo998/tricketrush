# Load Test Results

## Reservation race

This test validates the central TicketRush correctness invariant: concurrent attempts to reserve one available seat result in exactly one confirmed order.

### Scenario

- 200 virtual users each make one authenticated request.
- All requests target the same available seat.
- The expected outcome is exactly one `201 Created` and 199 `409 Conflict` responses.
- Any other response, more than one success, or fewer than 199 conflicts fails the k6 run.

### Run locally

Start PostgreSQL and the API in separate terminals:

```bash
cp .env.example .env
docker compose up -d postgres
cd backend
mvn spring-boot:run
```

Seed a resettable local event and available seat:

```bash
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < load-test/seed-reservation-race.sql
```

Run k6 from the repository root. With a local k6 installation:

```bash
BASE_URL=http://localhost:8080 \
EVENT_ID=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1 \
SEAT_ID=bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1 \
k6 run load-test/reservation-race.js
```

Or run the script in Docker (the host API address is preconfigured by the script):

```bash
docker run --rm -i \
  -e EVENT_ID=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1 \
  -e SEAT_ID=bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1 \
  -v "$PWD:/work" -w /work \
  grafana/k6 run load-test/reservation-race.js
```

### Acceptance criteria

| Metric | Required result |
|---|---:|
| `reservation_successes` | 1 |
| `reservation_conflicts` | 199 |
| `unexpected_responses` | 0 |
| `http_req_failed` | 0 |

The k6 script enforces these thresholds, so a completed run is a pass/fail artifact rather than a manually interpreted result. The backend integration suite separately verifies that the single winning response corresponds to one persisted order and one sold seat.

## Recorded local baseline

Run on 2026-08-16 against the local Spring Boot API and Docker PostgreSQL instance using the supplied fixture and `grafana/k6` container.

| Metric | Result |
|---|---:|
| Virtual users / iterations | 200 / 200 |
| `reservation_successes` | 1 |
| `reservation_conflicts` | 199 |
| `unexpected_responses` | 0 |
| `http_req_failed` | 0% |
| Request duration, p95 | 278.69 ms |
| Request duration, max | 334.38 ms |
| Persisted seat state | `SOLD`, version `1` |
| Persisted confirmed orders for the event | 1 |

This is a local correctness baseline, not a production capacity benchmark. Hardware, Docker allocation, connection-pool configuration, and deployment topology materially affect latency and throughput.
