-- Local-only, resettable fixture for reservation-race.js.
-- Do not run this against shared or production data.

BEGIN;

DELETE FROM payments
WHERE order_id IN (
    SELECT id FROM orders WHERE event_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'
);
DELETE FROM order_seats
WHERE order_id IN (
    SELECT id FROM orders WHERE event_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'
);
DELETE FROM orders WHERE event_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1';
DELETE FROM holds WHERE seat_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1';
DELETE FROM seats WHERE id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1';
DELETE FROM events WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1';

INSERT INTO events (id, name, venue_name, sale_start_at, event_start_at, status)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    'Reservation Race Load Test',
    'Local Test Arena',
    now() - interval '1 minute',
    now() + interval '1 day',
    'ON_SALE'
);

INSERT INTO seats (id, event_id, section, row, seat_number, price_cents, currency, status, version)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    'LOAD',
    '1',
    '1',
    10000,
    'USD',
    'AVAILABLE',
    0
);

COMMIT;
