let _token: string | null = null;
let _onUnauthorized: (() => void) | null = null;

export function setAuthToken(token: string | null): void {
  _token = token;
}

export function setUnauthorizedHandler(fn: (() => void) | null): void {
  _onUnauthorized = fn;
}

export function getToken(): string | null {
  return _token;
}

interface RequestOptions {
  skipAuthHandler?: boolean;
}

interface ApiResponse<T = unknown> {
  data?: T;
  message?: string;
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {}
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string>),
  };

  if (_token) {
    headers['Authorization'] = `Bearer ${_token}`;
  }

  const response = await fetch(path, { ...init, headers });
  let body: ApiResponse;
  try {
    body = await response.json();
  } catch (e) {
    // Non-JSON response body (e.g. proxy 502 HTML error page)
    const cause = e instanceof Error ? e.message : String(e);
    const err: Error & { status?: number } = new Error(
      `HTTP ${response.status}${cause ? ` (${cause})` : ''}`
    );
    err.status = response.status;
    throw err;
  }

  if (response.ok) {
    return body.data as T;
  }

  if (response.status === 401) {
    if (!options.skipAuthHandler && _onUnauthorized) {
      _onUnauthorized();
    }
    const err: Error & { status?: number } = new Error(body.message ?? 'Unauthorized');
    err.status = 401;
    throw err;
  }

  const err: Error & { status?: number; data?: unknown } = new Error(body.message ?? `HTTP ${response.status}`);
  err.status = response.status;
  if (response.status === 400) err.data = body.data;
  throw err;
}
