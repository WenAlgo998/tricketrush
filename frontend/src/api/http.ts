export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type ApiErrorBody = {
  error?: string;
  code?: string;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  if (init.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers });
  } catch {
    throw new ApiError("Unable to reach the TicketRush API. Please try again.", 0);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await parseJson(response);
  if (!response.ok) {
    const error = body as ApiErrorBody | undefined;
    throw new ApiError(
      error?.error ?? `Request failed with status ${response.status}.`,
      response.status,
      error?.code
    );
  }

  return body as T;
}

async function parseJson(response: Response): Promise<unknown> {
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return undefined;
  }

  return response.json();
}
