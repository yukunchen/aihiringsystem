import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => { vi.restoreAllMocks(); });

describe('jobs API', () => {
  it('listJobs() calls GET /api/jobs with pagination', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
    const { listJobs } = await import('./jobs');
    await listJobs(0, 10);
    expect(requestModule.request).toHaveBeenCalledWith('/api/jobs?page=0&size=10');
  });

  it('createJob() calls POST /api/jobs', async () => {
    const mockJob = { id: '1', title: 'SWE', departmentId: 'dept-1', departmentName: 'Eng', status: 'DRAFT', createdAt: '' };
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(mockJob);
    const { createJob } = await import('./jobs');
    const result = await createJob({ title: 'SWE', departmentId: 'dept-1', description: 'desc' });
    expect(requestModule.request).toHaveBeenCalledWith('/api/jobs', expect.objectContaining({ method: 'POST' }));
    expect(result.title).toBe('SWE');
  });

  it('changeJobStatus() calls PUT /api/jobs/:id/status', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({});
    const { changeJobStatus } = await import('./jobs');
    await changeJobStatus('job-1', 'PUBLISHED');
    expect(requestModule.request).toHaveBeenCalledWith(
      '/api/jobs/job-1/status',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ status: 'PUBLISHED' }) })
    );
  });

  it('getJob() calls GET /api/jobs/:id', async () => {
    const mockJob = { id: 'j1', title: 'SWE', departmentId: 'd1', departmentName: 'Eng', status: 'DRAFT', createdAt: '', description: 'desc' };
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(mockJob);
    const { getJob } = await import('./jobs');
    const result = await getJob('j1');
    expect(requestModule.request).toHaveBeenCalledWith('/api/jobs/j1');
    expect(result.title).toBe('SWE');
  });

  it('updateJob() calls PUT /api/jobs/:id', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({});
    const { updateJob } = await import('./jobs');
    await updateJob('j1', { title: 'Updated' });
    expect(requestModule.request).toHaveBeenCalledWith(
      '/api/jobs/j1',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ title: 'Updated' }) })
    );
  });

  it('deleteJob() calls DELETE /api/jobs/:id', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(undefined);
    const { deleteJob } = await import('./jobs');
    await deleteJob('job-1');
    expect(requestModule.request).toHaveBeenCalledWith('/api/jobs/job-1', { method: 'DELETE' });
  });
});
