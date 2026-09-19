import { request } from "./http";

export type AuthResponse = {
  userId: string;
  email: string;
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
};

type Credentials = {
  email: string;
  password: string;
};

export function login(credentials: Credentials): Promise<AuthResponse> {
  return request<AuthResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(credentials)
  });
}

export function register(credentials: Credentials): Promise<AuthResponse> {
  return request<AuthResponse>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(credentials)
  });
}
