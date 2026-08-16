import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const attempts = Number(__ENV.ATTEMPTS || 200);
const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const eventId = requiredEnv('EVENT_ID');
const seatId = requiredEnv('SEAT_ID');

http.setResponseCallback(http.expectedStatuses(201, 409));

export const options = {
  scenarios: {
    reservation_race: {
      executor: 'per-vu-iterations',
      vus: attempts,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    reservation_successes: ['count==1'],
    reservation_conflicts: [`count==${attempts - 1}`],
    unexpected_responses: ['count==0'],
    http_req_failed: ['rate==0'],
  },
};

const reservationSuccesses = new Counter('reservation_successes');
const reservationConflicts = new Counter('reservation_conflicts');
const unexpectedResponses = new Counter('unexpected_responses');

// A distinct account per run isolates the test from prior local test users.
export function setup() {
  const email = `load-test-${Date.now()}-${Math.random().toString(36).slice(2)}@example.test`;
  const registration = http.post(`${baseUrl}/api/auth/register`, JSON.stringify({
    email,
    password: 'load-test-password',
  }), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(registration, { 'load-test buyer registered': (response) => response.status === 201 });
  if (registration.status !== 201) {
    throw new Error(`Unable to register load-test buyer: HTTP ${registration.status}`);
  }
  return { accessToken: registration.json('accessToken') };
}

export default function (data) {
  const response = http.post(
    `${baseUrl}/api/events/${eventId}/seats/${seatId}/reserve`,
    null,
    { headers: { Authorization: `Bearer ${data.accessToken}` } },
  );

  if (response.status === 201) {
    reservationSuccesses.add(1);
  } else if (response.status === 409) {
    reservationConflicts.add(1);
  } else {
    unexpectedResponses.add(1);
  }
  check(response, {
    'reservation has an expected outcome': (result) => result.status === 201 || result.status === 409,
  });
}

function requiredEnv(name) {
  const value = __ENV[name];
  if (!value) {
    throw new Error(`${name} must be set`);
  }
  return value;
}
