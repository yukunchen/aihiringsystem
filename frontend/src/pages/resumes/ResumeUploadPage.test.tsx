import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../api/resumes', () => ({ uploadResume: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ token: 'tok', isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() })),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

import * as resumesApi from '../../api/resumes';
import ResumeUploadPage from './ResumeUploadPage';

beforeEach(() => { vi.clearAllMocks(); });

describe('ResumeUploadPage', () => {
  it('renders upload dragger', () => {
    render(<MemoryRouter><ResumeUploadPage /></MemoryRouter>);
    expect(screen.getByText(/drag/i)).toBeInTheDocument();
  });

  it('accepts only pdf, docx, and txt file types', () => {
    render(<MemoryRouter><ResumeUploadPage /></MemoryRouter>);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    expect(input.accept).toBe('.pdf,.docx,.txt');
  });

  it('shows correct supported file type hint text', () => {
    render(<MemoryRouter><ResumeUploadPage /></MemoryRouter>);
    expect(screen.getByText(/Supports PDF, DOCX, TXT/i)).toBeInTheDocument();
  });

  it('uploads file and redirects to /resumes on success', async () => {
    vi.mocked(resumesApi.uploadResume).mockResolvedValueOnce({
      id: 'r1', fileName: 'cv.pdf', fileType: 'PDF', source: 'MANUAL', status: 'UPLOADED', uploadedAt: '',
    });

    render(<MemoryRouter><ResumeUploadPage /></MemoryRouter>);

    const file = new File(['pdf content'], 'cv.pdf', { type: 'application/pdf' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(input, file);
    await userEvent.click(screen.getByTestId('upload-btn'));

    await waitFor(() => expect(resumesApi.uploadResume).toHaveBeenCalledWith(file));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/resumes'));
  });

  it('shows error message on upload failure', async () => {
    const err = new Error('File too large');
    vi.mocked(resumesApi.uploadResume).mockRejectedValueOnce(err);

    render(<MemoryRouter><ResumeUploadPage /></MemoryRouter>);
    const file = new File(['pdf content'], 'cv.pdf', { type: 'application/pdf' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(input, file);
    await userEvent.click(screen.getByTestId('upload-btn'));

    await waitFor(() => expect(screen.getByText('File too large')).toBeInTheDocument());
  });
});
