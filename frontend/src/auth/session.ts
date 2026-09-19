import type { AuthResponse } from "../api/auth";

const STORAGE_KEY = "ticketrush.auth.session";

export type AuthSession = Pick<AuthResponse, "userId" | "email" | "accessToken" | "tokenType"> & {
  expiresAt: number;
};

export function createSession(response: AuthResponse, now = Date.now()): AuthSession {
  return {
    userId: response.userId,
    email: response.email,
    accessToken: response.accessToken,
    tokenType: response.tokenType,
    expiresAt: now + response.expiresIn * 1_000
  };
}

export function readSession(now = Date.now()): AuthSession | null {
  const stored = window.sessionStorage.getItem(STORAGE_KEY);
  if (stored === null) {
    return null;
  }

  try {
    const session = JSON.parse(stored) as Partial<AuthSession>;
    if (!isValidSession(session) || session.expiresAt <= now) {
      clearSession();
      return null;
    }

    return session;
  } catch {
    clearSession();
    return null;
  }
}

export function saveSession(session: AuthSession): void {
  window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  window.sessionStorage.removeItem(STORAGE_KEY);
}

function isValidSession(session: Partial<AuthSession>): session is AuthSession {
  return (
    typeof session.userId === "string" &&
    typeof session.email === "string" &&
    typeof session.accessToken === "string" &&
    session.tokenType === "Bearer" &&
    typeof session.expiresAt === "number"
  );
}
