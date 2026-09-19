import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, request } from "./http";

afterEach(() => vi.unstubAllGlobals());

describe("request", () => {
  it("serializes JSON requests and returns a typed response", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { headers: { "content-type": "application/json" } })
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(request<{ ok: boolean }>("/api/example", { method: "POST", body: "{}" })).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/example",
      expect.objectContaining({ method: "POST", headers: expect.any(Headers) })
    );
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("Content-Type")).toBe("application/json");
  });

  it("maps an API error body into ApiError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: "Invalid email or password", code: "INVALID_CREDENTIALS" }), {
          status: 401,
          headers: { "content-type": "application/json" }
        })
      )
    );

    await expect(request("/api/auth/login")).rejects.toEqual(
      new ApiError("Invalid email or password", 401, "INVALID_CREDENTIALS")
    );
  });
});
