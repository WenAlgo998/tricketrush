# Database Design

## PostgreSQL Schema (core tables)

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  venue_name TEXT NOT NULL,
  sale_start_at TIMESTAMPTZ NOT NULL,
  event_start_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'ON_SALE', 'CLOSED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (event_start_at > sale_start_at)
);

CREATE TABLE seats (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id UUID NOT NULL REFERENCES events(id),
  section TEXT NOT NULL,
  row TEXT NOT NULL,
  seat_number TEXT NOT NULL,
  price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
  currency CHAR(3) NOT NULL DEFAULT 'USD',
  status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
  version INT NOT NULL DEFAULT 0,
  UNIQUE (event_id, section, row, seat_number)
);

CREATE TABLE holds (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  seat_id UUID NOT NULL REFERENCES seats(id),
  user_id UUID NOT NULL REFERENCES users(id),
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'CONSUMED')),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  released_at TIMESTAMPTZ
);

CREATE TABLE orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  event_id UUID NOT NULL REFERENCES events(id),
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'EXPIRED')),
  idempotency_key UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, idempotency_key)
);

CREATE TABLE order_seats (
  order_id UUID NOT NULL REFERENCES orders(id),
  seat_id UUID NOT NULL REFERENCES seats(id),
  PRIMARY KEY (order_id, seat_id)
);

CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL REFERENCES orders(id),
  status TEXT NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
  provider_ref TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_id UUID NOT NULL,
  event_type TEXT NOT NULL,
  payload JSONB NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## Indexes to add
- `seats (event_id, status)` — fast seat-map queries
- `holds (expires_at)` — fast expiry sweep
- `holds (seat_id, status)` — validates checkout and expiry efficiently
- `orders (user_id, created_at)` — buyer order history
- `outbox_events (published, next_attempt_at, created_at)` — fast publisher polling
- Partial unique index — prevents more than one active hold for a seat:

```sql
CREATE UNIQUE INDEX uq_active_hold_per_seat
  ON holds (seat_id)
  WHERE status = 'ACTIVE';
```

## Concurrency Rules (critical)
Every reservation path must use a conditional update inside a transaction. Stage 1 immediately sells a seat only when it is available:

```sql
UPDATE seats SET status = 'SOLD', version = version + 1
WHERE id = :seatId AND event_id = :eventId AND status = 'AVAILABLE';
```

Stage 2 transitions `AVAILABLE → HELD` only if the update matches the currently-read version, then inserts the `holds` record in the same transaction:
```sql
UPDATE seats
SET status = 'HELD', version = version + 1
WHERE id = :seatId AND status = 'AVAILABLE' AND version = :expectedVersion;
-- 0 rows updated => conflict, someone else got there first
```

Checkout and expiry must also use conditional transitions tied to the specific active hold. The expiry job may release a seat only after it changes that hold from `ACTIVE` to `EXPIRED` in the same transaction; it must not release a seat based solely on an old timestamp or seat status. Checkout likewise consumes only active holds owned by the authenticated buyer. These rules prevent a stale cleanup job from releasing a consumed or newly reassigned seat.

`order_seats` must contain only seats from the order's `event_id`. The service validates that invariant while creating the order; a later schema refinement may enforce it with composite foreign keys.

The outbox publisher claims ready rows with `FOR UPDATE SKIP LOCKED`, increments `attempt_count` on failure, and uses `next_attempt_at` for retry backoff. Kafka consumers must be idempotent because delivery is at least once.

## Redis Keys
| Key pattern | Purpose | TTL |
|---|---|---|
| `ratelimit:{userId}` | per-user request throttle | rolling window |
| `waitingroom:{eventId}:admitted` | counter of admitted users | sale duration |

Redis is never the only record of a hold. A scheduled job expires durable hold rows and atomically returns their seats to `AVAILABLE`; Redis may cache the status but must be rebuilt from PostgreSQL after a restart.
