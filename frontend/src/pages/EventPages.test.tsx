import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { AuthProvider } from "../auth/AuthContext";

const eventId = "3ed1615a-4300-4511-8dbf-a987b378bbe7";
const catalogEvent = {
  id: eventId,
  name: "Summer Sound",
  venueName: "Riverside Pavilion",
  saleStartAt: "2026-05-01T14:00:00Z",
  eventStartAt: "2026-06-20T20:00:00Z",
  status: "ON_SALE"
};

afterEach(() => vi.unstubAllGlobals());

describe("event discovery", () => {
  it("renders event cards from the paginated catalog response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({
      content: [catalogEvent],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1
    })));

    renderApp("/events");

    expect(await screen.findByRole("heading", { name: "Summer Sound" })).toBeInTheDocument();
    expect(screen.getByText("Riverside Pavilion")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /view event and seats/i })).toHaveAttribute("href", `/events/${eventId}`);
  });

  it("renders the event metadata and grouped read-only seat map", async () => {
    const fetchMock = vi.fn((input: string) => {
      if (input === `/api/events/${eventId}`) {
        return Promise.resolve(jsonResponse(catalogEvent));
      }
      if (input === `/api/events/${eventId}/seats`) {
        return Promise.resolve(jsonResponse([
          { id: "a1", section: "A", row: "1", seatNumber: "1", priceCents: 4500, currency: "USD", status: "AVAILABLE", version: 0 },
          { id: "a2", section: "A", row: "1", seatNumber: "2", priceCents: 4500, currency: "USD", status: "HELD", version: 1 },
          { id: "b1", section: "B", row: "2", seatNumber: "1", priceCents: 3000, currency: "USD", status: "SOLD", version: 1 }
        ]));
      }
      return Promise.reject(new Error(`Unexpected request: ${input}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    renderApp(`/events/${eventId}`);

    expect(await screen.findByRole("heading", { name: "Summer Sound" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Section A" })).toBeInTheDocument();
    expect(screen.getByLabelText(/section A, row 1, seat 1: Available/i)).toBeInTheDocument();
    expect(screen.getByText("Available")).toBeInTheDocument();
    expect(screen.getByText("Held")).toBeInTheDocument();
    expect(screen.getByText("Sold")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(`/api/events/${eventId}`, expect.anything());
    expect(fetchMock).toHaveBeenCalledWith(`/api/events/${eventId}/seats`, expect.anything());
  });

  it("shows a retry affordance when the catalog cannot be loaded", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ error: "Service unavailable" }, 503)));

    renderApp("/events");

    expect(await screen.findByRole("alert")).toHaveTextContent("Service unavailable");
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });
});

function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>
  );
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" }
  });
}
