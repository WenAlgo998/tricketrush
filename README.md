# TicketRush

A high-concurrency ticket reservation platform demonstrating queueing, seat-hold concurrency control, real-time updates, and reliable event-driven payment handling.

**Status:** 📋 Design finalized — implementation has not started

## Docs

- [Product Requirements](docs/PRD.md)
- [Architecture & Design Decisions](docs/architecture.md)
- [Database Design](docs/database-design.md)
- [API Design](docs/api-design.md)
- [Sequence Diagrams](docs/sequence-diagrams.md)

## Stack

Spring Boot · React · PostgreSQL · Redis · Kafka · WebSockets

## Build Stages

1. Event catalog, auth, static seat map, and atomic PostgreSQL reservations
2. Seat holds with durable expiry + optimistic locking
3. Live seat updates (WebSocket) + load-test script
4. Waiting room, rate limiting, Kafka payment events, retry/DLQ, audit log

See `docs/architecture.md` for exit criteria per stage.

## Planned Local Setup

The application services and local infrastructure files will be added during implementation. The intended prerequisites are Java 21, Apache Maven 3.9+, Node.js 22+, and Docker Desktop.

Once those files exist, the local workflow will be:

```bash
cp .env.example .env
docker compose up -d postgres
```

Verify that PostgreSQL is ready before starting the API:

```bash
docker compose ps
```

The API reads the same root `.env` file when started from `backend/`, so its database connection settings stay aligned with Docker Compose.

> **Resetting local data:** PostgreSQL applies `POSTGRES_*` values only when its data volume is first created. If you intentionally change these values, reset the local database with `docker compose down -v` before starting it again. This permanently deletes local database data.

Start the API in one terminal:

```bash
cd backend
mvn spring-boot:run
```

Flyway applies all committed database migrations automatically at application startup. Do not modify a migration after it has been applied; create a new versioned migration instead.

Start the web app in another:

```bash
cd frontend
npm install
npm run dev
```

Verify API health at `http://localhost:8080/actuator/health`. See the component READMEs for checks and troubleshooting.

Stage 1 intentionally confirms a single available seat without payment. This isolates and validates the atomic reservation invariant before checkout and asynchronous payment processing are introduced in Stage 4.

## Load Test Results

## Explicit Exclusions

No browser automation, bot behavior, or anything intended to evade real ticketing platforms' queues or terms of service. Payments are fully mocked.
