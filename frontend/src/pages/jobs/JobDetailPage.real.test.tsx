import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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

// Simulate the ACTUAL backend response format (nested department/createdBy)
const realBackendResponse = {
  id: 'j1',
  title: 'Backend Engineer',
  department: { id: 'd1', name: 'Engineering' },
  departmentId: 'd1',
  departmentName: 'Engineering',
  createdBy: { id: 'u1', username: 'admin' },
  status: 'DRAFT',
  createdAt: '2026-03-01T00:00:00Z',
  updatedAt: '2026-03-01T00:00:00Z',
  description: 'Build APIs',
  requirements: 'Java 3yrs',
  skills: 'Java, Spring',
  education: "Bachelor's",
  experience: '3 years',
  salaryRange: '20-30k',
  location: 'Beijing',
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
  vi.mocked(jobsApi.getJob).mockResolvedValue(
    realBackendResponse as import('../../api/jobs').JobDetail,
  );
  vi.mocked(deptApi.listDepartments).mockResolvedValue([{ id: 'd1', name: 'Engineering' }]);
  vi.mocked(matchApi.match).mockResolvedValue({ jobId: 'j1', results: [] });
});

describe('JobDetailPage with REAL backend response', () => {
  it('should display job title after load (not stuck spinning)', async () => {
    renderDetailPage();
    await waitFor(() => expect(screen.getByText('Backend Engineer')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('should display job description', async () => {
    renderDetailPage();
    await waitFor(() => expect(screen.getByText('Build APIs')).toBeInTheDocument());
  });

  it('should display status tag', async () => {
    renderDetailPage();
    await waitFor(() => expect(screen.getByText('DRAFT')).toBeInTheDocument());
  });

  it('should display department name', async () => {
    renderDetailPage();
    await waitFor(() => expect(screen.getByText('Engineering')).toBeInTheDocument());
  });

  it('should NOT be stuck on loading spinner', async () => {
    renderDetailPage();
    // Wait for the title to appear (proves loading completed)
    await waitFor(() => {
      expect(screen.getByText('Backend Engineer')).toBeInTheDocument();
    }, { timeout: 5000 });
    // The spinner should be gone once content is loaded
    expect(screen.queryByText(/loading/i)).not.toBeInTheDocument();
  });
});
