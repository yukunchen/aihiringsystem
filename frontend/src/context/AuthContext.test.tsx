import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// Mock API modules
vi.mock('../api/auth', () => ({
  login: vi.fn(),
  refresh: vi.fn(),
  logout: vi.fn(),
}));
vi.mock('../api/request', () => ({
  setAuthToken: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import * as authApi from '../api/auth';
import * as requestModule from '../api/request';
import { AuthProvider, useAuth } from './AuthContext';

// Helper: create a fake JWT with payload
function makeJwt(payload: object): string {
  const encoded = btoa(JSON.stringify(payload));
  return `header.${encoded}.sig`;
}

const mockAccessToken = makeJwt({ sub: 'user-1', username: 'alice', roles: ['ROLE_HR'] });
const mockRefreshToken = 'refresh-abc';

function TestConsumer() {
  const { token, user, isInitializing, login, logout } = useAuth();
  if (isInitializing) return <div>Loading...</div>;
  if (!token) return <button onClick={() => login('alice', 'pass')}>Login</button>;
  return (
    <div>
      <span>Hello {user?.username}</span>
      <button onClick={() => logout()}>Logout</button>
    </div>
  );
}

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
});

describe('AuthContext', () => {
  it('shows loading then login button when no refresh token in localStorage', async () => {
    render(<AuthProvider><TestConsumer /></AuthProvider>);
    expect(screen.getByText('Loading...')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Login')).toBeInTheDocument());
  });

  it('restores session on mount when refresh token exists in localStorage', async () => {
    localStorage.setItem('refreshToken', mockRefreshToken);
    vi.mocked(authApi.refresh).mockResolvedValueOnce({
      accessToken: mockAccessToken,
      refreshToken: mockRefreshToken,
    });

    render(<AuthProvider><TestConsumer /></AuthProvider>);
    await waitFor(() => expect(screen.getByText('Hello alice')).toBeInTheDocument());
    expect(authApi.refresh).toHaveBeenCalledWith(mockRefreshToken);
    expect(requestModule.setAuthToken).toHaveBeenCalledWith(mockAccessToken);
  });

  it('clears state when refresh fails on mount', async () => {
    localStorage.setItem('refreshToken', mockRefreshToken);
    vi.mocked(authApi.refresh).mockRejectedValueOnce(new Error('Unauthorized'));

    render(<AuthProvider><TestConsumer /></AuthProvider>);
    await waitFor(() => expect(screen.getByText('Login')).toBeInTheDocument());
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });

  it('login() stores tokens and decodes user', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      accessToken: mockAccessToken,
      refreshToken: mockRefreshToken,
    });

    render(<AuthProvider><TestConsumer /></AuthProvider>);
    await waitFor(() => screen.getByText('Login'));
    await userEvent.click(screen.getByText('Login'));

    await waitFor(() => expect(screen.getByText('Hello alice')).toBeInTheDocument());
    expect(localStorage.getItem('refreshToken')).toBe(mockRefreshToken);
    expect(requestModule.setAuthToken).toHaveBeenCalledWith(mockAccessToken);
  });

  it('logout() clears token and localStorage', async () => {
    localStorage.setItem('refreshToken', mockRefreshToken);
    vi.mocked(authApi.refresh).mockResolvedValueOnce({
      accessToken: mockAccessToken,
      refreshToken: mockRefreshToken,
    });
    vi.mocked(authApi.logout).mockResolvedValueOnce(undefined);

    render(<AuthProvider><TestConsumer /></AuthProvider>);
    await waitFor(() => screen.getByText('Hello alice'));
    await userEvent.click(screen.getByText('Logout'));

    await waitFor(() => expect(screen.getByText('Login')).toBeInTheDocument());
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(requestModule.setAuthToken).toHaveBeenCalledWith(null);
  });
});
