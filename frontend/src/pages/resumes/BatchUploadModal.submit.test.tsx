import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../../api/resumes', () => ({
  uploadResumes: vi.fn(),
}));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ token: 'tok', isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() })),
}));

import * as resumesApi from '../../api/resumes';
import { BatchUploadModal } from './BatchUploadModal';

beforeEach(() => { vi.clearAllMocks(); });

describe('BatchUploadModal submit flow', () => {
  const onClose = vi.fn();
  const onSuccess = vi.fn();

  it('uploads multiple files and calls onSuccess', async () => {
    vi.mocked(resumesApi.uploadResumes).mockResolvedValueOnce({
      total: 2, succeeded: 2, failed: 0,
      results: [
        { originalIndex: 0, fileName: 'a.pdf', status: 'UPLOADED', resumeId: 'r1', error: null },
        { originalIndex: 1, fileName: 'b.pdf', status: 'UPLOADED', resumeId: 'r2', error: null },
      ],
    });

    render(<BatchUploadModal open={true} onClose={onClose} onSuccess={onSuccess} />);

    const fileA = new File(['a'], 'a.pdf', { type: 'application/pdf' });
    const fileB = new File(['b'], 'b.pdf', { type: 'application/pdf' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(input, [fileA, fileB]);

    await waitFor(() => expect(screen.getByText('a.pdf')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText('b.pdf')).toBeInTheDocument());

    // Target the footer <button> element specifically (not the Upload.Dragger span)
    const uploadBtn = screen.getAllByRole('button', { name: /upload/i })
      .find((el) => el.tagName === 'BUTTON')!;
    await userEvent.click(uploadBtn);

    await waitFor(() => expect(resumesApi.uploadResumes).toHaveBeenCalled());
    const calledFiles = vi.mocked(resumesApi.uploadResumes).mock.calls[0][0];
    expect(calledFiles).toHaveLength(2);
    expect(calledFiles[0].name).toBe('a.pdf');
    expect(calledFiles[1].name).toBe('b.pdf');

    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });

  it('shows error status when upload fails', async () => {
    vi.mocked(resumesApi.uploadResumes).mockRejectedValueOnce(new Error('Network error'));

    render(<BatchUploadModal open={true} onClose={onClose} onSuccess={onSuccess} />);

    const file = new File(['c'], 'c.pdf', { type: 'application/pdf' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(input, file);

    await waitFor(() => expect(screen.getByText('c.pdf')).toBeInTheDocument());
    const uploadBtn = screen.getAllByRole('button', { name: /upload/i })
      .find((el) => el.tagName === 'BUTTON')!;
    await userEvent.click(uploadBtn);

    await waitFor(() => expect(resumesApi.uploadResumes).toHaveBeenCalled());
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('disables upload button when no files selected', () => {
    render(<BatchUploadModal open={true} onClose={onClose} onSuccess={onSuccess} />);
    const uploadBtn = screen.getAllByRole('button', { name: /upload/i })
      .find((el) => el.tagName === 'BUTTON')!;
    expect(uploadBtn).toBeDisabled();
  });
});
