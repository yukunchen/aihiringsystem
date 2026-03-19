import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('auth API', () => {
  it('login() calls POST /api/auth/login and returns tokens', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresIn: 7200,
    });

    const { login } = await import('./auth');
    const result = await login('admin', 'password');

    expect(requestModule.request).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({ method: 'POST' })
    );
    expect(result).toEqual({ accessToken: 'access', refreshToken: 'refresh' });
  });

  it('refresh() calls POST /api/auth/refresh with refreshToken in body', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
      expiresIn: 7200,
    });

    const { refresh } = await import('./auth');
    await refresh('old-refresh');

    const [, init] = (requestModule.request as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(JSON.parse(init.body)).toMatchObject({ refreshToken: 'old-refresh' });
  });

  it('logout() calls POST /api/auth/logout with refreshToken in body', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(undefined);

    const { logout } = await import('./auth');
    await logout('my-refresh');

    const [path, init] = (requestModule.request as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(path).toBe('/api/auth/logout');
    expect(JSON.parse(init.body)).toMatchObject({ refreshToken: 'my-refresh' });
  });
});
