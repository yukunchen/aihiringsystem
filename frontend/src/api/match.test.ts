import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => { vi.restoreAllMocks(); });

describe('match API', () => {
  it('match() calls POST /api/match with jobId and topK', async () => {
    const mockResponse = { jobId: 'job-1', results: [] };
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(mockResponse);
    const { match } = await import('./match');
    const result = await match('job-1', 10);
    expect(requestModule.request).toHaveBeenCalledWith(
      '/api/match',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ jobId: 'job-1', topK: 10 }) })
    );
    expect(result.jobId).toBe('job-1');
  });
});
