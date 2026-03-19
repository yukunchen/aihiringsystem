import { request } from './request';
import type { Page } from './types';

export interface JobListItem {
  id: string;
  title: string;
  departmentId: string;
  departmentName: string;
  status: string;
  createdAt: string;
}

export interface JobDetail extends JobListItem {
  description: string;
  requirements?: string;
  skills?: string;
  education?: string;
  experience?: string;
  salaryRange?: string;
  location?: string;
}

export interface CreateJobRequest {
  title: string;
  departmentId: string;
  description: string;
  requirements?: string;
  skills?: string;
  education?: string;
  experience?: string;
  salaryRange?: string;
  location?: string;
}

export type UpdateJobRequest = Partial<CreateJobRequest>;

export async function listJobs(page: number, size: number): Promise<Page<JobListItem>> {
  return request<Page<JobListItem>>(`/api/jobs?page=${page}&size=${size}`);
}

export async function getJob(id: string): Promise<JobDetail> {
  return request<JobDetail>(`/api/jobs/${id}`);
}

export async function createJob(data: CreateJobRequest): Promise<JobDetail> {
  return request<JobDetail>('/api/jobs', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateJob(id: string, data: UpdateJobRequest): Promise<JobDetail> {
  return request<JobDetail>(`/api/jobs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function changeJobStatus(id: string, status: string): Promise<JobDetail> {
  return request<JobDetail>(`/api/jobs/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  });
}

export async function deleteJob(id: string): Promise<void> {
  return request<void>(`/api/jobs/${id}`, { method: 'DELETE' });
}
