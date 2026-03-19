import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => { vi.restoreAllMocks(); });

describe('departments API', () => {
  it('listDepartments() calls GET /api/departments', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce([{ id: '1', name: 'Engineering' }]);
    const { listDepartments } = await import('./departments');
    const result = await listDepartments();
    expect(requestModule.request).toHaveBeenCalledWith('/api/departments');
    expect(result[0].name).toBe('Engineering');
  });
});
