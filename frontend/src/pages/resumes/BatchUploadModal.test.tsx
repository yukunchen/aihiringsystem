import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BatchUploadModal } from './BatchUploadModal';

describe('BatchUploadModal', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => { vi.clearAllMocks(); });

  it('renders drag-drop area and title', () => {
    render(<BatchUploadModal open onClose={mockOnClose} onSuccess={mockOnSuccess} />);
    expect(screen.getByText('Batch Upload Resumes')).toBeInTheDocument();
    expect(screen.getByText('Click or drag files to upload')).toBeInTheDocument();
  });
});
