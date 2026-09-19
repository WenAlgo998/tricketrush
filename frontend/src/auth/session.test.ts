import { afterEach, describe, expect, it } from "vitest";
import { clearSession, createSession, readSession, saveSession } from "./session";

const authResponse = {
  userId: "3ed1615a-4300-4511-8dbf-a987b378bbe7",
  email: "buyer@example.com",
  accessToken: "test-token",
  tokenType: "Bearer" as const,
  expiresIn: 900
};

afterEach(clearSession);

describe("session storage", () => {
  it("persists a valid, unexpired authentication session", () => {
    const session = createSession(authResponse, 1_000);
    saveSession(session);

    expect(readSession(1_001)).toEqual(session);
  });

  it("removes expired sessions before returning them", () => {
    saveSession(createSession(authResponse, 1_000));

    expect(readSession(901_000)).toBeNull();
    expect(readSession()).toBeNull();
  });

  it("removes malformed session data", () => {
    window.sessionStorage.setItem("ticketrush.auth.session", "not-json");

    expect(readSession()).toBeNull();
    expect(window.sessionStorage.getItem("ticketrush.auth.session")).toBeNull();
  });
});
