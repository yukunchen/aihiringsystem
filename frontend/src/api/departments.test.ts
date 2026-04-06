import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => { vi.restoreAllMocks(); });

describe('departments API', () => {
  it('listDepartments() flattens tree response', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce([
      { id: '1', name: '总部', children: [
        { id: '2', name: '研发部', children: [] },
        { id: '3', name: '人事部', children: [] },
      ]},
    ]);
    const { listDepartments } = await import('./departments');
    const result = await listDepartments();
    expect(requestModule.request).toHaveBeenCalledWith('/api/departments');
    expect(result).toEqual([
      { id: '1', name: '总部' },
      { id: '2', name: '研发部' },
      { id: '3', name: '人事部' },
    ]);
  });
});
