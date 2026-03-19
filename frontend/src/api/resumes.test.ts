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

  it('uploadResume() POSTs multipart and returns resume on success', async () => {
    const mockResume = { id: 'r1', fileName: 'cv.pdf', fileType: 'PDF', source: 'MANUAL', status: 'UPLOADED', uploadedAt: '2026-03-01T00:00:00Z' };
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
