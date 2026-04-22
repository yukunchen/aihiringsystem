import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { STATUS_COLORS, STATUS_LABELS } from './resumeStatus';
import ResumeListPage from './ResumeListPage';
import * as resumesApi from '../../api/resumes';

vi.mock('../../api/resumes');

describe('ResumeListPage date column', () => {
  it('renders Uploaded date from createdAt (not Invalid Date)', async () => {
    vi.mocked(resumesApi.listResumes).mockResolvedValueOnce({
      content: [{
        id: 'r1', fileName: 'cv.pdf', fileType: 'PDF', source: 'MANUAL',
        status: 'UPLOADED', createdAt: '2026-04-20T10:00:00Z',
      }],
      totalElements: 1, totalPages: 1, number: 0, size: 10,
    });
    render(<MemoryRouter><ResumeListPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('cv.pdf')).toBeInTheDocument());
    const expected = new Date('2026-04-20T10:00:00Z').toLocaleDateString();
    expect(screen.getByText(expected)).toBeInTheDocument();
    expect(screen.queryByText('Invalid Date')).not.toBeInTheDocument();
  });
});

describe('STATUS_LABELS', () => {
  it('maps UPLOADED to Uploaded', () => {
    expect(STATUS_LABELS['UPLOADED']).toBe('Uploaded');
  });

  it('maps TEXT_EXTRACTED to Text Extracted', () => {
    expect(STATUS_LABELS['TEXT_EXTRACTED']).toBe('Text Extracted');
  });

  it('maps AI_PROCESSED to AI Processed', () => {
    expect(STATUS_LABELS['AI_PROCESSED']).toBe('AI Processed');
  });

  it('maps VECTORIZATION_FAILED to Vectorization Failed', () => {
    expect(STATUS_LABELS['VECTORIZATION_FAILED']).toBe('Vectorization Failed');
  });

  it('does not contain old frontend-only keys (PARSED, PARSING, etc.)', () => {
    expect(STATUS_LABELS['PARSED']).toBeUndefined();
    expect(STATUS_LABELS['PARSING']).toBeUndefined();
    expect(STATUS_LABELS['PARSE_FAILED']).toBeUndefined();
    expect(STATUS_LABELS['VECTORIZING']).toBeUndefined();
    expect(STATUS_LABELS['VECTORIZED']).toBeUndefined();
  });
});

describe('STATUS_COLORS', () => {
  it('assigns default color for UPLOADED', () => {
    expect(STATUS_COLORS['UPLOADED']).toBe('default');
  });

  it('assigns processing color for TEXT_EXTRACTED', () => {
    expect(STATUS_COLORS['TEXT_EXTRACTED']).toBe('processing');
  });

  it('assigns success color for AI_PROCESSED', () => {
    expect(STATUS_COLORS['AI_PROCESSED']).toBe('success');
  });

  it('assigns error color for VECTORIZATION_FAILED', () => {
    expect(STATUS_COLORS['VECTORIZATION_FAILED']).toBe('error');
  });
});

