import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../api/jobs', () => ({ createJob: vi.fn() }));
vi.mock('../../api/departments', () => ({ listDepartments: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ token: 'tok', isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() })),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

import * as jobsApi from '../../api/jobs';
import * as deptApi from '../../api/departments';
import JobCreatePage from './JobCreatePage';

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(deptApi.listDepartments).mockResolvedValue([{ id: 'd1', name: 'Engineering' }]);
});

describe('JobCreatePage', () => {
  it('renders all required form fields', async () => {
    render(<MemoryRouter><JobCreatePage /></MemoryRouter>);
    await waitFor(() => screen.getByText('Engineering'));
    expect(screen.getByPlaceholderText(/title/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/description/i)).toBeInTheDocument();
  });

  it('submits form and redirects to new job detail page', async () => {
    vi.mocked(jobsApi.createJob).mockResolvedValueOnce({
      id: 'j1', title: 'SWE', departmentId: 'd1', departmentName: 'Engineering',
      status: 'DRAFT', createdAt: '', description: 'desc',
    });

    render(<MemoryRouter><JobCreatePage /></MemoryRouter>);
    await waitFor(() => screen.getByText('Engineering'));

    await userEvent.type(screen.getByPlaceholderText(/title/i), 'SWE');

    // Select department
    await userEvent.click(screen.getByRole('combobox'));
    await waitFor(() => screen.getByText('Engineering'));
    await userEvent.click(screen.getByText('Engineering'));

    await userEvent.type(screen.getByPlaceholderText(/description/i), 'desc');
    await userEvent.click(screen.getByRole('button', { name: /submit/i }));

    await waitFor(() => expect(jobsApi.createJob).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'SWE', departmentId: 'd1', description: 'desc' })
    ));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/jobs/j1'));
  });

  it('shows per-field validation errors on 400 response', async () => {
    const err: Error & { data?: unknown } = new Error('Validation failed');
    err.data = { title: 'Title is required' };
    vi.mocked(jobsApi.createJob).mockRejectedValueOnce(err);
    vi.mocked(deptApi.listDepartments).mockResolvedValue([{ id: 'd1', name: 'Engineering' }]);

    render(<MemoryRouter><JobCreatePage /></MemoryRouter>);
    await waitFor(() => screen.getByText('Engineering'));

    await userEvent.type(screen.getByPlaceholderText(/title/i), 'x');
    await userEvent.click(screen.getByRole('combobox'));
    await userEvent.click(screen.getByText('Engineering'));
    await userEvent.type(screen.getByPlaceholderText(/description/i), 'x');
    await userEvent.click(screen.getByRole('button', { name: /submit/i }));

    await waitFor(() => expect(screen.getByText('Title is required')).toBeInTheDocument());
  });

  it('shows error message and stays on form when createJob throws a generic error', async () => {
    vi.mocked(jobsApi.createJob).mockRejectedValueOnce(new Error('Internal Server Error'));

    render(<MemoryRouter><JobCreatePage /></MemoryRouter>);
    await waitFor(() => screen.getByText('Engineering'));

    await userEvent.type(screen.getByPlaceholderText(/title/i), 'Test Job');
    await userEvent.click(screen.getByRole('combobox'));
    await userEvent.click(screen.getByText('Engineering'));
    await userEvent.type(screen.getByPlaceholderText(/description/i), 'Test desc');
    await userEvent.click(screen.getByRole('button', { name: /submit/i }));

    await waitFor(() => expect(jobsApi.createJob).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText('Internal Server Error')).toBeInTheDocument());
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
