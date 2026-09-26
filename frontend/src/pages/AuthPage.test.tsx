import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { AuthProvider } from "../auth/AuthContext";

afterEach(() => vi.unstubAllGlobals());

describe("authentication pages", () => {
  it("signs in and redirects to the event catalog", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn((input: string) => {
      if (input === "/api/auth/login") {
        return Promise.resolve(new Response(
          JSON.stringify({
            userId: "3ed1615a-4300-4511-8dbf-a987b378bbe7",
            email: "buyer@example.com",
            accessToken: "test-token",
            tokenType: "Bearer",
            expiresIn: 900
          }),
          { headers: { "content-type": "application/json" } }
        ));
      }
      if (input === "/api/events?page=0&size=12") {
        return Promise.resolve(new Response(
          JSON.stringify({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
          { headers: { "content-type": "application/json" } }
        ));
      }
      return Promise.reject(new Error(`Unexpected request: ${input}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    );

    await user.type(screen.getByLabelText("Email address"), "BUYER@example.com");
    await user.type(screen.getByLabelText("Password"), "correct-horse");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("heading", { name: "Make room for a good night." })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/auth/login",
      expect.objectContaining({ method: "POST", body: JSON.stringify({ email: "buyer@example.com", password: "correct-horse" }) })
    );
  });

  it("shows the API-provided authentication failure", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: "Invalid email or password", code: "INVALID_CREDENTIALS" }), {
          status: 401,
          headers: { "content-type": "application/json" }
        })
      )
    );

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    );

    await user.type(screen.getByLabelText("Email address"), "buyer@example.com");
    await user.type(screen.getByLabelText("Password"), "incorrect-password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
  });
});
