import { request } from "./http";

export type EventStatus = "SCHEDULED" | "ON_SALE" | "CLOSED";
export type SeatStatus = "AVAILABLE" | "HELD" | "SOLD";

export type CatalogEvent = {
  id: string;
  name: string;
  venueName: string;
  saleStartAt: string;
  eventStartAt: string;
  status: EventStatus;
};

export type Seat = {
  id: string;
  section: string;
  row: string;
  seatNumber: string;
  priceCents: number;
  currency: string;
  status: SeatStatus;
  version: number;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export function getEvents(page: number, size: number): Promise<PageResponse<CatalogEvent>> {
  const parameters = new URLSearchParams({ page: String(page), size: String(size) });
  return request<PageResponse<CatalogEvent>>(`/api/events?${parameters}`);
}

export function getEvent(eventId: string): Promise<CatalogEvent> {
  return request<CatalogEvent>(`/api/events/${eventId}`);
}

export function getSeats(eventId: string): Promise<Seat[]> {
  return request<Seat[]>(`/api/events/${eventId}/seats`);
}
