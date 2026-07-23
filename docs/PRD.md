# PRD — TicketRush

## Problem
Ticket sales for high-demand events create sudden traffic spikes that lead to double-bookings, unfair access, and system crashes. This project builds a self-contained platform that demonstrates how to handle this correctly.

## Goals
- Zero double-bookings under concurrent load
- Controlled, fair admission during spikes (waiting room)
- Real-time seat status for all users
- Reliable, idempotent payment handling (mocked)
- Measured, documented behavior under simulated load

## Non-Goals
- Real payment processing
- Bot detection / CAPTCHA / evading real ticketing platforms
- Multi-region or production-scale deployment

## Users
- **Buyer:** browses seat map, joins waiting room, holds a seat, checks out
- **(Optional/stretch) Admin:** creates events, views audit log

## Event Lifecycle
Events progress through `SCHEDULED`, `ON_SALE`, and `CLOSED`. A scheduled event cannot be reserved before `sale_start_at`; closed events reject new reservations and holds. A future administrative pause state is out of scope for the initial build.

## User Stories
1. As a buyer, I want to join a queue when sales open so access feels fair, not a stampede.
2. As a buyer, I want to see live seat availability so I don't waste time on sold seats.
3. As a buyer, I want my seat held for a few minutes so I have time to check out without losing it.
4. As a buyer, if my payment fails or times out, I want the seat released automatically.
5. As the system, I must never confirm the same seat for two different orders.

## Availability and Authorization Rules
- Seat availability shown in the UI is advisory. The server is authoritative, so a buyer can still receive a conflict if another request won the concurrent reservation race.
- Buyers may access, release, and check out only their own holds and orders.
- Stage 1 reserves one seat per request. Multi-seat checkout is introduced later through the hold and order flows.
- Active holds expire after a configurable five-minute duration. A checkout succeeds only when its hold is still active at the database transaction boundary.

## Success Metrics (for this project's own validation, not production KPIs)
- No seat is ever confirmed for more than one order
- For N concurrent first attempts for the same available seat (target N ≥ 200), exactly one request succeeds
- Admission rate stays within configured limit (e.g. X users/min) during simulated spike
- p95 hold-to-confirm latency documented

## Scope by Stage
See `architecture.md` and root README for the 4-stage build plan and exit criteria per stage.

## Explicit Exclusions
No browser automation, bot behavior, or anything intended to evade real ticketing sites' queues or terms of service.
