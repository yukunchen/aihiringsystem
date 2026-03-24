import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

class MockResizeObserver {
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
}
global.ResizeObserver = MockResizeObserver as any;

vi.mock('../../api/resumes', () => ({
  listResumes: vi.fn(),
  deleteResume: vi.fn(),
  downloadResume: vi.fn(),
}));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ user: { username: 'alice' }, token: 'tok', isInitializing: false, login: vi.fn(), logout: vi.fn() })),
}));

import * as resumesApi from '../../api/resumes';
import ResumeListPage from './ResumeListPage';

const mockPage = {
  content: [
    { id: 'r1', fileName: 'alice_cv.pdf', fileType: 'PDF', source: 'MANUAL', status: 'PARSED', uploadedAt: '2026-03-01T10:00:00Z' },
  ],
  totalElements: 1, totalPages: 1, number: 0, size: 10,
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(resumesApi.listResumes).mockResolvedValue(mockPage);
});

describe('ResumeListPage', () => {
  it('renders the resume table after loading', async () => {
    render(<MemoryRouter><ResumeListPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('alice_cv.pdf')).toBeInTheDocument());
    expect(screen.getByText('PARSED')).toBeInTheDocument();
  });

  it('renders empty state when no resumes', async () => {
    vi.mocked(resumesApi.listResumes).mockResolvedValueOnce({ ...mockPage, content: [], totalElements: 0 });
    render(<MemoryRouter><ResumeListPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(/No resumes yet/i)).toBeInTheDocument());
  });

  it('should show batch upload button', () => {
    render(<MemoryRouter><ResumeListPage /></MemoryRouter>);
    expect(screen.getByText('Batch Upload')).toBeInTheDocument();
  });

  it('calls deleteResume on confirm delete', async () => {
    vi.mocked(resumesApi.deleteResume).mockResolvedValueOnce(undefined);
    vi.mocked(resumesApi.listResumes).mockResolvedValue(mockPage);

    render(<MemoryRouter><ResumeListPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('alice_cv.pdf'));

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));
    // Antd Popconfirm renders an "OK" button in the popover
    await waitFor(() => screen.getByRole('button', { name: /ok/i }));
    await userEvent.click(screen.getByRole('button', { name: /ok/i }));

    await waitFor(() => expect(resumesApi.deleteResume).toHaveBeenCalledWith('r1'));
  });
});
