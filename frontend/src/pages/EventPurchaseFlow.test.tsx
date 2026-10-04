import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { AuthProvider } from "../auth/AuthContext";

vi.mock("../live/useSeatStatusSubscription", () => ({
  useSeatStatusSubscription: () => "connected"
}));

const eventId = "3ed1615a-4300-4511-8dbf-a987b378bbe7";
const accessToken = "test-access-token";

afterEach(() => vi.unstubAllGlobals());

describe("seat holds and checkout", () => {
  it("creates an authenticated hold and submits the selected hold with an idempotency key", async () => {
    window.sessionStorage.setItem("ticketrush.auth.session", JSON.stringify({
      userId: "2aaf367b-9544-485b-9ed4-834fa36116b7",
      email: "buyer@example.com",
      accessToken,
      tokenType: "Bearer",
      expiresAt: Date.now() + 60_000
    }));
    const user = userEvent.setup();
    const fetchMock = vi.fn((input: string, init?: RequestInit) => {
      if (input === `/api/events/${eventId}`) {
        return Promise.resolve(jsonResponse(event));
      }
      if (input === `/api/events/${eventId}/seats`) {
        return Promise.resolve(jsonResponse([seat]));
      }
      if (input === `/api/holds?eventId=${eventId}`) {
        return Promise.resolve(jsonResponse([]));
      }
      if (input === `/api/events/${eventId}/seats/${seat.id}/hold` && init?.method === "POST") {
        return Promise.resolve(jsonResponse({ holdId: "d9b206ef-2ec9-4966-9427-46cc9a3435c1", expiresAt: "2030-01-01T00:05:00Z" }, 201));
      }
      if (input === "/api/orders" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({ orderId: "7b4c9f59-00f1-4466-8962-b38d5f1ee854", status: "PENDING" }, 202));
      }
      if (input === "/api/orders/7b4c9f59-00f1-4466-8962-b38d5f1ee854") {
        return Promise.resolve(jsonResponse({ orderId: "7b4c9f59-00f1-4466-8962-b38d5f1ee854", status: "PENDING" }));
      }
      return Promise.reject(new Error(`Unexpected request: ${input}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <MemoryRouter initialEntries={[`/events/${eventId}`]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    );

    await screen.findByRole("heading", { name: event.name });
    await user.click(screen.getByRole("button", { name: /hold section A, row 1, seat 1/i }));

    expect(await screen.findByRole("button", { name: "Checkout 1 seat" })).toBeInTheDocument();
    const holdCall = fetchMock.mock.calls.find(([path]) => path === `/api/events/${eventId}/seats/${seat.id}/hold`);
    expect(holdCall).toBeDefined();
    expect(new Headers(holdCall?.[1]?.headers).get("Authorization")).toBe(`Bearer ${accessToken}`);
    expect(holdCall?.[1]?.body).toBe(JSON.stringify({ expectedVersion: 0 }));

    await user.click(screen.getByRole("button", { name: "Checkout 1 seat" }));

    expect(await screen.findByText("Payment is processing. This page will update automatically.")).toBeInTheDocument();
    const checkoutCall = fetchMock.mock.calls.find(([path]) => path === "/api/orders");
    expect(checkoutCall).toBeDefined();
    expect(checkoutCall?.[1]?.body).toBe(JSON.stringify({ holdIds: ["d9b206ef-2ec9-4966-9427-46cc9a3435c1"] }));
    expect(new Headers(checkoutCall?.[1]?.headers).get("Authorization")).toBe(`Bearer ${accessToken}`);
    expect(new Headers(checkoutCall?.[1]?.headers).get("Idempotency-Key")).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
  });
});

const event = {
  id: eventId,
  name: "Summer Sound",
  venueName: "Riverside Pavilion",
  saleStartAt: "2026-05-01T14:00:00Z",
  eventStartAt: "2026-06-20T20:00:00Z",
  status: "ON_SALE"
};

const seat = {
  id: "1235d7dc-bf2b-4134-a90d-438609fa2227",
  section: "A",
  row: "1",
  seatNumber: "1",
  priceCents: 4500,
  currency: "USD",
  status: "AVAILABLE",
  version: 0
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" }
  });
}
