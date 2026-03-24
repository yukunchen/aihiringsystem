import { getToken, request } from './request';
import type { Page } from './types';

export type { Page };

export interface ResumeListItem {
  id: string;
  fileName: string;
  fileType: string;
  source: string;
  status: string;
  uploadedAt: string;
}

export async function listResumes(page: number, size: number): Promise<Page<ResumeListItem>> {
  return request<Page<ResumeListItem>>(`/api/resumes?page=${page}&size=${size}`);
}

export async function uploadResume(file: File): Promise<ResumeListItem> {
  const formData = new FormData();
  formData.append('file', file);
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const response = await fetch('/api/resumes/upload?source=MANUAL', {
    method: 'POST',
    headers,
    body: formData,
  });
  const body = await response.json();
  if (response.ok) return body.data;
  if (response.status === 400) {
    const err: Error & { data?: unknown } = new Error(body.message);
    err.data = body.data;
    throw err;
  }
  throw new Error(body.message ?? `HTTP ${response.status}`);
}

export async function downloadResume(id: string): Promise<Blob> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const response = await fetch(`/api/resumes/${id}/download`, { headers });
  if (!response.ok) {
    const text = await response.text().catch(() => '');
    const err: Error & { status?: number } = new Error(
      text || `Download failed (${response.status})`
    );
    err.status = response.status;
    throw err;
  }
  return response.blob();
}

export async function deleteResume(id: string): Promise<void> {
  return request<void>(`/api/resumes/${id}`, { method: 'DELETE' });
}

export interface BatchUploadResult {
  originalIndex: number;
  fileName: string;
  status: string;
  resumeId: string | null;
  error: string | null;
}

export interface BatchUploadResponse {
  total: number;
  succeeded: number;
  failed: number;
  results: BatchUploadResult[];
}

export async function uploadResumes(files: File[]): Promise<BatchUploadResponse> {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const response = await fetch('/api/resumes/upload?source=MANUAL', {
    method: 'POST',
    headers,
    body: formData,
  });
  const body = await response.json();
  if (response.ok) return body.data;
  throw new Error(body.message ?? `HTTP ${response.status}`);
}
