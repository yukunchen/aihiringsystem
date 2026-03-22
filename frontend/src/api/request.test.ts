import { describe, it, expect, vi, beforeEach } from 'vitest';
import { request, setAuthToken, setUnauthorizedHandler } from './request';

function mockFetch(status: number, body: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as Response);
}

function mockFetchNonJson(status: number) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
    ok: false,
    status,
    json: () => Promise.reject(new SyntaxError('Unexpected token <')),
  } as unknown as Response);
}

beforeEach(() => {
  setAuthToken(null);
  setUnauthorizedHandler(null);
});

describe('request()', () => {
  it('returns data on 2xx response', async () => {
    mockFetch(200, { code: 200, message: 'ok', data: { id: '1' } });
    const result = await request<{ id: string }>('/api/test');
    expect(result).toEqual({ id: '1' });
  });

  it('injects Authorization header when token is set', async () => {
    setAuthToken('my-token');
    const spy = mockFetch(200, { code: 200, message: 'ok', data: {} });
    await request('/api/test');
    const headers = spy.mock.calls[0][1]?.headers as Record<string, string>;
    expect(headers['Authorization']).toBe('Bearer my-token');
  });

  it('does not inject Authorization header when token is null', async () => {
    const spy = mockFetch(200, { code: 200, message: 'ok', data: {} });
    await request('/api/test');
    const headers = (spy.mock.calls[0][1]?.headers ?? {}) as Record<string, string>;
    expect(headers['Authorization']).toBeUndefined();
  });

  it('calls unauthorized handler on 401 and throws', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetch(401, { code: 401, message: 'Unauthorized', data: null });
    await expect(request('/api/test')).rejects.toThrow();
    expect(handler).toHaveBeenCalled();
  });

  it('skips unauthorized handler on 401 when skipAuthHandler is true', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetch(401, { code: 401, message: 'Unauthorized', data: null });
    await expect(request('/api/test', {}, { skipAuthHandler: true })).rejects.toThrow();
    expect(handler).not.toHaveBeenCalled();
  });

  it('throws error with data property on 400', async () => {
    mockFetch(400, {
      code: 400,
      message: 'Validation failed',
      data: { title: 'Title is required' },
    });
    const error = await request('/api/test').catch((e: unknown) => e as { data: unknown });
    expect((error as { data: unknown }).data).toEqual({ title: 'Title is required' });
  });

  it('throws error with message on other non-2xx', async () => {
    mockFetch(500, { code: 500, message: 'Internal server error', data: null });
    await expect(request('/api/test')).rejects.toThrow('Internal server error');
  });

  it('throws graceful error when response body is not JSON', async () => {
    mockFetchNonJson(502);
    const err = await request('/api/test').catch((e: unknown) => e as Error & { status?: number });
    expect(err).toBeInstanceOf(Error);
    expect(err.status).toBe(502);
    expect(err.message).toMatch(/HTTP 502/);
  });
});
