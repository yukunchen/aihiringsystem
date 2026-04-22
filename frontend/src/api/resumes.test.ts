import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => { vi.restoreAllMocks(); });

describe('resumes API', () => {
  it('listResumes() calls GET /api/resumes with pagination', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
    const { listResumes } = await import('./resumes');
    await listResumes(0, 10);
    expect(requestModule.request).toHaveBeenCalledWith('/api/resumes?page=0&size=10');
  });

  it('deleteResume() calls DELETE /api/resumes/:id', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(undefined);
    const { deleteResume } = await import('./resumes');
    await deleteResume('abc-123');
    expect(requestModule.request).toHaveBeenCalledWith('/api/resumes/abc-123', { method: 'DELETE' });
  });

  it('downloadResume() calls GET /api/resumes/:id/download', async () => {
    const mockBlob = new Blob(['pdf'], { type: 'application/pdf' });
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      blob: () => Promise.resolve(mockBlob),
    } as Response);
    const { downloadResume } = await import('./resumes');
    const result = await downloadResume('abc-123');
    expect(result).toBe(mockBlob);
  });

  it('downloadResume() throws error with status and body text on non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 404,
      text: () => Promise.resolve('Resume not found'),
    } as unknown as Response);
    const { downloadResume } = await import('./resumes');
    const err = await downloadResume('abc-123').catch((e: unknown) => e as Error & { status?: number });
    expect(err).toBeInstanceOf(Error);
    expect(err.status).toBe(404);
    expect(err.message).toBe('Resume not found');
  });

  it('downloadResume() falls back to generic message on empty error body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 502,
      text: () => Promise.resolve(''),
    } as unknown as Response);
    const { downloadResume } = await import('./resumes');
    const err = await downloadResume('abc-123').catch((e: unknown) => e as Error & { status?: number });
    expect(err).toBeInstanceOf(Error);
    expect(err.status).toBe(502);
    expect(err.message).toBe('Download failed (502)');
  });

  it('uploadResumes() POSTs multiple files and returns batch response', async () => {
  const mockResponse = {
    code: 200, message: 'ok',
    data: {
      total: 2, succeeded: 2, failed: 0,
      results: [
        { originalIndex: 0, fileName: 'a.pdf', status: 'UPLOADED', resumeId: 'r1', error: null },
        { originalIndex: 1, fileName: 'b.pdf', status: 'UPLOADED', resumeId: 'r2', error: null },
      ]
    }
  };
  vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
    ok: true, status: 200, json: () => Promise.resolve(mockResponse),
  } as Response);
  const { uploadResumes } = await import('./resumes');
  const files = [
    new File(['a'], 'a.pdf', { type: 'application/pdf' }),
    new File(['b'], 'b.pdf', { type: 'application/pdf' }),
  ];
  const result = await uploadResumes(files);
  expect(result.total).toBe(2);
  expect(result.succeeded).toBe(2);
  expect(result.results[0].fileName).toBe('a.pdf');
  const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
  expect(url).toBe('/api/resumes/upload?source=MANUAL');
  expect(init.method).toBe('POST');
});

it('uploadResume() POSTs multipart and returns resume on success', async () => {
    const mockResume = { id: 'r1', fileName: 'cv.pdf', fileType: 'PDF', source: 'MANUAL', status: 'UPLOADED', createdAt: '2026-03-01T00:00:00Z' };
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'ok', data: mockResume }),
    } as Response);
    const { uploadResume } = await import('./resumes');
    const file = new File(['content'], 'cv.pdf', { type: 'application/pdf' });
    const result = await uploadResume(file);
    expect(result).toEqual(mockResume);
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe('/api/resumes/upload?source=MANUAL');
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
  });
});
