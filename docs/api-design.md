# API Design (Stage 1 baseline — extend in later stages)

## Auth
- `POST /api/auth/register`
- `POST /api/auth/login` → returns JWT
- Authenticated requests use `Authorization: Bearer <JWT>`. Missing or invalid credentials return `401`; authenticated users attempting to access another buyer's resource return `403`.

## Events & Seats
- `GET /api/events` — paginated event list.
  - Query parameters: `page` (zero-based, default `0`, maximum `10000`) and `size` (default `20`, maximum `100`).
  - `200` → `{ content: [...], page, size, totalElements, totalPages }`
- `GET /api/events/{eventId}` — event detail.
  - `200` → `{ id, name, venueName, saleStartAt, eventStartAt, status }`
  - `404` → `{ error: "Event not found", code: "EVENT_NOT_FOUND" }`
- `GET /api/events/{eventId}/seats` — seat map with current status. Each seat includes `id`, `section`, `row`, `seatNumber`, `priceCents`, `currency`, `status`, and `version`.

## Reservations (Stage 1)
- `POST /api/events/{eventId}/seats/{seatId}/reserve` — immediately confirms one available seat for the authenticated user.
  - The server performs an atomic conditional update; it must never use a read-then-write reservation.
  - `201` → `{ orderId, status: "CONFIRMED" }`
  - `409` → seat is no longer available.

## Holds (Stage 2+)
- `POST /api/events/{eventId}/seats/{seatId}/hold`
  - body: `{ expectedVersion }`; identity comes from the JWT, never a client-supplied `userId`.
  - `201` → `{ holdId, expiresAt }`
  - 409 → seat already held/sold (version mismatch)
- `DELETE /api/holds/{holdId}` — releases the authenticated buyer's hold early; an already inactive hold returns `204` without changing state.

## Checkout (Stage 4)
- `POST /api/orders`
  - requires `Idempotency-Key: <UUID>` request header; uniqueness is scoped to the authenticated user.
  - body: `{ holdIds: [...] }`
  - 202 → `{ orderId, status: "PENDING" }`
- `GET /api/orders/{orderId}` — poll or receive via WebSocket

## Waiting Room (Stage 4)
- `POST /api/events/{eventId}/queue/join` → `{ position, estimatedWaitSeconds }` (position is an estimate)
- `GET /api/events/{eventId}/queue/status` → `{ admitted: boolean, token? }`

## WebSocket (Stage 3+)
- `WS /ws/events/{eventId}/seats`
- Server → client message: `{ seatId, status, timestamp }`
- WebSocket connections authenticate and authorize subscriptions. Messages are best-effort notifications; clients refetch `GET /api/events/{eventId}/seats` after reconnecting or when an update sequence appears incomplete.

## Error Conventions
- 409 Conflict — concurrency/version mismatch
- 429 Too Many Requests — rate limit exceeded
- 400 Bad Request — malformed request or invalid field value
- 401 Unauthorized — missing or invalid JWT
- 403 Forbidden — resource is not owned by the authenticated user
- 404 Not Found — requested resource does not exist
- All error bodies: `{ error: string, code: string, traceId?: string }`
