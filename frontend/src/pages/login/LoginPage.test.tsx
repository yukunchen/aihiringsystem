import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '../../context/AuthContext';
import LoginPage from './LoginPage';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useAuth).mockReturnValue({
    token: null, isInitializing: false, user: null,
    login: vi.fn(), logout: vi.fn(),
  });
});

describe('LoginPage', () => {
  it('renders username and password fields', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByPlaceholderText(/username/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/password/i)).toBeInTheDocument();
  });

  it('calls login() and redirects to /jobs on success', async () => {
    const mockLogin = vi.fn().mockResolvedValueOnce(undefined);
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: false, user: null, login: mockLogin, logout: vi.fn() });

    render(<MemoryRouter initialEntries={[{ pathname: '/login', state: {} }]}><LoginPage /></MemoryRouter>);
    await userEvent.type(screen.getByPlaceholderText(/username/i), 'alice');
    await userEvent.type(screen.getByPlaceholderText(/password/i), 'secret');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => expect(mockLogin).toHaveBeenCalledWith('alice', 'secret'));
    expect(mockNavigate).toHaveBeenCalledWith('/jobs', expect.anything());
  });

  it('shows error message on login failure (401)', async () => {
    const mockLogin = vi.fn().mockRejectedValueOnce(new Error('Invalid credentials'));
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: false, user: null, login: mockLogin, logout: vi.fn() });

    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    await userEvent.type(screen.getByPlaceholderText(/username/i), 'alice');
    await userEvent.type(screen.getByPlaceholderText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => expect(screen.getByText('Invalid credentials')).toBeInTheDocument());
  });

  it('redirects to state.from after successful login', async () => {
    const mockLogin = vi.fn().mockResolvedValueOnce(undefined);
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: false, user: null, login: mockLogin, logout: vi.fn() });

    render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: { pathname: '/resumes' } } }]}>
        <LoginPage />
      </MemoryRouter>
    );
    await userEvent.type(screen.getByPlaceholderText(/username/i), 'alice');
    await userEvent.type(screen.getByPlaceholderText(/password/i), 'pass');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/resumes', expect.anything()));
  });
});
