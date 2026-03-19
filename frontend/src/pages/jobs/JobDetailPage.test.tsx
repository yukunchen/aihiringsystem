import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

vi.mock('../../api/jobs', () => ({
  getJob: vi.fn(),
  updateJob: vi.fn(),
  changeJobStatus: vi.fn(),
  deleteJob: vi.fn(),
}));
vi.mock('../../api/departments', () => ({ listDepartments: vi.fn() }));
vi.mock('../../api/match', () => ({ match: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ token: 'tok', isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() })),
}));

import * as jobsApi from '../../api/jobs';
import * as deptApi from '../../api/departments';
import * as matchApi from '../../api/match';
import JobDetailPage from './JobDetailPage';

const mockJob = {
  id: 'j1', title: 'Backend Engineer', departmentId: 'd1', departmentName: 'Engineering',
  status: 'DRAFT', createdAt: '2026-03-01T00:00:00Z',
  description: 'Build APIs', requirements: 'Java 3yrs', skills: 'Java, Spring',
  education: "Bachelor's", experience: '3 years', salaryRange: '20-30k', location: 'Beijing',
};

function renderDetailPage() {
  return render(
    <MemoryRouter initialEntries={['/jobs/j1']}>
      <Routes>
        <Route path="/jobs/:id" element={<JobDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(jobsApi.getJob).mockResolvedValue(mockJob);
  vi.mocked(deptApi.listDepartments).mockResolvedValue([{ id: 'd1', name: 'Engineering' }]);
  vi.mocked(matchApi.match).mockResolvedValue({ jobId: 'j1', results: [] });
});

describe('JobDetailPage — JD Info', () => {
  it('displays job details after load', async () => {
    renderDetailPage();
    await waitFor(() => expect(screen.getByText('Backend Engineer')).toBeInTheDocument());
    expect(screen.getByText('Build APIs')).toBeInTheDocument();
    expect(screen.getByText('Java 3yrs')).toBeInTheDocument();
  });

  it('calls changeJobStatus when status is changed', async () => {
    vi.mocked(jobsApi.changeJobStatus).mockResolvedValueOnce({ ...mockJob, status: 'PUBLISHED' });
    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));

    // Open status select and pick PUBLISHED
    await userEvent.click(screen.getByRole('combobox'));
    await waitFor(() => screen.getByText('PUBLISHED'));
    await userEvent.click(screen.getByText('PUBLISHED'));

    await waitFor(() => expect(jobsApi.changeJobStatus).toHaveBeenCalledWith('j1', 'PUBLISHED'));
  });

  it('shows inline status error on invalid transition', async () => {
    const err = new Error('Invalid status transition');
    vi.mocked(jobsApi.changeJobStatus).mockRejectedValueOnce(err);
    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));

    await userEvent.click(screen.getByRole('combobox'));
    await waitFor(() => screen.getByText('PUBLISHED'));
    await userEvent.click(screen.getByText('PUBLISHED'));

    await waitFor(() => expect(screen.getByText('Invalid status transition')).toBeInTheDocument());
  });

  it('enters edit mode and saves via PUT', async () => {
    vi.mocked(jobsApi.updateJob).mockResolvedValueOnce({ ...mockJob, title: 'Updated Title' });
    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));

    await userEvent.click(screen.getByRole('button', { name: /edit/i }));
    const titleInput = screen.getByDisplayValue('Backend Engineer');
    await userEvent.clear(titleInput);
    await userEvent.type(titleInput, 'Updated Title');
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(jobsApi.updateJob).toHaveBeenCalledWith('j1', expect.objectContaining({ title: 'Updated Title' })));
  });
});

describe('JobDetailPage — AI Matching', () => {
  it('runs match and shows results table', async () => {
    vi.mocked(matchApi.match).mockResolvedValueOnce({
      jobId: 'j1',
      results: [
        { resumeId: 'r1', vectorScore: 0.95, llmScore: 87, reasoning: 'Strong match', highlights: ['Java'] },
      ],
    });

    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));

    await userEvent.click(screen.getByRole('button', { name: /find matching/i }));

    await waitFor(() => expect(screen.getByText('Strong match')).toBeInTheDocument());
    expect(screen.getByText('Java')).toBeInTheDocument();
  });

  it('shows 422 warning when JD not indexed', async () => {
    vi.mocked(matchApi.match).mockRejectedValueOnce(Object.assign(new Error('JD not vectorized yet'), { status: 422 }));

    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));
    await userEvent.click(screen.getByRole('button', { name: /find matching/i }));

    await waitFor(() => expect(screen.getByText(/not been indexed/i)).toBeInTheDocument());
  });

  it('shows 503 error when AI service is down', async () => {
    vi.mocked(matchApi.match).mockRejectedValueOnce(Object.assign(new Error('AI service unavailable'), { status: 503 }));

    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));
    await userEvent.click(screen.getByRole('button', { name: /find matching/i }));

    await waitFor(() => expect(screen.getByText(/unavailable/i)).toBeInTheDocument());
  });
});
