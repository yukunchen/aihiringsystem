import { describe, it, expect } from 'vitest';
import { STATUS_COLORS, STATUS_LABELS } from './resumeStatus';
import { formatUploadDate } from './resumeDate';

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

describe('formatUploadDate', () => {
  it('returns formatted date for a valid ISO string', () => {
    expect(formatUploadDate('2026-04-22T04:14:25Z')).not.toBe('Invalid Date');
    expect(formatUploadDate('2026-04-22T04:14:25Z')).not.toBe('-');
  });

  it('returns dash for null', () => {
    expect(formatUploadDate(null)).toBe('-');
  });

  it('returns dash for undefined (covers uploadedAt/createdAt mismatch regression from issue #125)', () => {
    expect(formatUploadDate(undefined)).toBe('-');
  });

  it('returns dash for empty string', () => {
    expect(formatUploadDate('')).toBe('-');
  });

  it('returns dash for unparseable input instead of "Invalid Date"', () => {
    expect(formatUploadDate('not-a-date')).toBe('-');
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

