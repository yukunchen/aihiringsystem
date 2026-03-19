import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '../context/AuthContext';
import ProtectedRoute from './ProtectedRoute';

describe('ProtectedRoute', () => {
  it('shows spinner while initializing', () => {
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: true, user: null, login: vi.fn(), logout: vi.fn() });
    render(
      <MemoryRouter initialEntries={['/jobs']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/jobs" element={<div>Jobs Page</div>} />
          </Route>
          <Route path="/login" element={<div>Login</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.queryByText('Jobs Page')).not.toBeInTheDocument();
    expect(screen.queryByText('Login')).not.toBeInTheDocument();
  });

  it('redirects to /login when not authenticated', () => {
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() });
    render(
      <MemoryRouter initialEntries={['/jobs']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/jobs" element={<div>Jobs Page</div>} />
          </Route>
          <Route path="/login" element={<div>Login</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText('Login')).toBeInTheDocument();
    expect(screen.queryByText('Jobs Page')).not.toBeInTheDocument();
  });

  it('renders children when authenticated', () => {
    vi.mocked(useAuth).mockReturnValue({
      token: 'tok',
      isInitializing: false,
      user: { id: '1', username: 'alice', roles: [] },
      login: vi.fn(),
      logout: vi.fn(),
    });
    render(
      <MemoryRouter initialEntries={['/jobs']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/jobs" element={<div>Jobs Page</div>} />
          </Route>
          <Route path="/login" element={<div>Login</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText('Jobs Page')).toBeInTheDocument();
  });
});
