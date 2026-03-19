import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../api/jobs', () => ({
  listJobs: vi.fn(),
  deleteJob: vi.fn(),
}));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ token: 'tok', isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() })),
}));

import * as jobsApi from '../../api/jobs';
import JobListPage from './JobListPage';

const mockPage = {
  content: [
    { id: 'j1', title: 'Backend Engineer', departmentId: 'd1', departmentName: 'Engineering', status: 'DRAFT', createdAt: '2026-03-01T10:00:00Z' },
  ],
  totalElements: 1, totalPages: 1, number: 0, size: 10,
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(jobsApi.listJobs).mockResolvedValue(mockPage);
});

describe('JobListPage', () => {
  it('renders job table after loading', async () => {
    render(<MemoryRouter><JobListPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('Backend Engineer')).toBeInTheDocument());
    expect(screen.getByText('Engineering')).toBeInTheDocument();
  });

  it('renders empty state when no jobs', async () => {
    vi.mocked(jobsApi.listJobs).mockResolvedValueOnce({ ...mockPage, content: [], totalElements: 0 });
    render(<MemoryRouter><JobListPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(/create/i)).toBeInTheDocument());
  });

  it('calls deleteJob on confirm delete', async () => {
    vi.mocked(jobsApi.deleteJob).mockResolvedValueOnce(undefined);
    render(<MemoryRouter><JobListPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('Backend Engineer'));

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));
    await waitFor(() => screen.getByRole('button', { name: /ok/i }));
    await userEvent.click(screen.getByRole('button', { name: /ok/i }));

    await waitFor(() => expect(jobsApi.deleteJob).toHaveBeenCalledWith('j1'));
  });
});
