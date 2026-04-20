import { request } from './request';

export interface MatchResultItem {
  resumeId: string;
  candidateName: string;
  vectorScore: number;
  llmScore: number;
  reasoning: string;
  highlights: string[];
}

export interface MatchResponse {
  jobId: string;
  results: MatchResultItem[];
}

export async function match(jobId: string, topK: number): Promise<MatchResponse> {
  return request<MatchResponse>('/api/match', {
    method: 'POST',
    body: JSON.stringify({ jobId, topK }),
  });
}
