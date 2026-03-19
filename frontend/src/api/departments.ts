import { request } from './request';

export interface Department {
  id: string;
  name: string;
}

export async function listDepartments(): Promise<Department[]> {
  return request<Department[]>('/api/departments');
}
