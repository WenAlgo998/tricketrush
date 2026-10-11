# Contributing to TicketRush

TicketRush is a portfolio project that prioritizes correctness at the ticket-ownership boundary. Small, focused pull requests are preferred so concurrency and recovery behavior remain easy to review.

## Prerequisites

- Java 21 and Maven 3.9+
- Node.js 22+ and npm 10+
- Docker Desktop

## Before opening a pull request

Run the checks relevant to your change from the repository root:

```bash
cd frontend && npm ci && npm test && npm run build
cd ../backend && mvn verify
cd .. && docker compose --env-file .env.example config --quiet
git diff --check
```

The backend integration suite uses Testcontainers, so Docker must be running. Do not modify an existing Flyway migration after it has been applied; add a new versioned migration instead.

## Pull-request guidelines

- Keep database writes transactional and preserve PostgreSQL as the source of truth for ticket ownership.
- Add or update automated tests for behavior changes, especially concurrency, ownership, idempotency, and recovery paths.
- Update the API and architecture documentation when externally observable behavior or system guarantees change.
- Do not add real payment credentials, browser automation, or functionality intended to evade third-party ticketing controls.
