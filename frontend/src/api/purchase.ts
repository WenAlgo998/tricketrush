import { authenticatedRequest } from "./http";

export type SeatHold = {
  holdId: string;
  expiresAt: string;
};

export type ActiveSeatHold = SeatHold & {
  seatId: string;
};

export type Order = {
  orderId: string;
  status: "PENDING" | "CONFIRMED" | "FAILED";
};

export function createSeatHold(
  eventId: string,
  seatId: string,
  expectedVersion: number,
  accessToken: string
): Promise<SeatHold> {
  return authenticatedRequest<SeatHold>(`/api/events/${eventId}/seats/${seatId}/hold`, accessToken, {
    method: "POST",
    body: JSON.stringify({ expectedVersion })
  });
}

export function releaseSeatHold(holdId: string, accessToken: string): Promise<void> {
  return authenticatedRequest<void>(`/api/holds/${holdId}`, accessToken, { method: "DELETE" });
}

export function getActiveSeatHolds(eventId: string, accessToken: string): Promise<ActiveSeatHold[]> {
  const parameters = new URLSearchParams({ eventId });
  return authenticatedRequest<ActiveSeatHold[]>(`/api/holds?${parameters}`, accessToken);
}

export function checkout(
  holdIds: string[],
  idempotencyKey: string,
  accessToken: string
): Promise<Order> {
  return authenticatedRequest<Order>("/api/orders", accessToken, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ holdIds })
  });
}

export function getOrder(orderId: string, accessToken: string): Promise<Order> {
  return authenticatedRequest<Order>(`/api/orders/${orderId}`, accessToken);
}
