import { request } from './request';

interface TokenData {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export async function login(
  username: string,
  password: string
): Promise<{ accessToken: string; refreshToken: string }> {
  const data = await request<TokenData>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
  return { accessToken: data.accessToken, refreshToken: data.refreshToken };
}

export async function refresh(
  refreshToken: string
): Promise<{ accessToken: string; refreshToken: string }> {
  const data = await request<TokenData>(
    '/api/auth/refresh',
    {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    },
    { skipAuthHandler: true }
  );
  return { accessToken: data.accessToken, refreshToken: data.refreshToken };
}

export async function logout(refreshToken: string): Promise<void> {
  await request<void>('/api/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  });
}
