import type { Memoir, MemoirComment, MemoirSort, MemoirStudyOption, PageResponse } from "../types";
import { apiFetch } from "./client";

export interface MemoirPayload {
  title: string;
  content: string;
}

export interface MemoirCreatePayload extends MemoirPayload {
  studyId: string;
}

export interface MemoirListParams {
  page?: number;
  keyword?: string;
  sortBy?: MemoirSort;
}

export interface MemoirListResult {
  memoirs: Memoir[];
  page: number;
  totalPages: number;
}

function toListResult(result: PageResponse<Memoir>): MemoirListResult {
  return { memoirs: result.content, page: result.number, totalPages: result.totalPages };
}

export async function listMemoirs(params: MemoirListParams = {}): Promise<MemoirListResult> {
  const { page = 0, keyword, sortBy } = params;
  const query = new URLSearchParams({ page: String(page) });
  if (keyword) query.set("keyword", keyword);
  if (sortBy) query.set("sortBy", sortBy);

  const result = await apiFetch<PageResponse<Memoir>>(`/api/memoirs?${query.toString()}`);
  return toListResult(result);
}

// "나의 활동" 탭: 내가 작성한 회고록만.
export async function listMyMemoirs(page = 0): Promise<MemoirListResult> {
  const result = await apiFetch<PageResponse<Memoir>>(`/api/memoirs/mine?page=${page}`);
  return toListResult(result);
}

export async function getMemoir(memoirId: string): Promise<Memoir> {
  return apiFetch<Memoir>(`/api/memoirs/${memoirId}`);
}

export async function listMyStudyOptions(): Promise<MemoirStudyOption[]> {
  return apiFetch<MemoirStudyOption[]>("/api/memoirs/my-studies");
}

export async function createMemoir(payload: MemoirCreatePayload): Promise<Memoir> {
  return apiFetch<Memoir>("/api/memoirs", {
    method: "POST",
    body: JSON.stringify({
      studyId: Number(payload.studyId),
      title: payload.title,
      content: payload.content,
    }),
  });
}

export async function updateMemoir(memoirId: string, payload: MemoirPayload): Promise<Memoir> {
  return apiFetch<Memoir>(`/api/memoirs/${memoirId}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function deleteMemoir(memoirId: string): Promise<void> {
  return apiFetch<void>(`/api/memoirs/${memoirId}`, { method: "DELETE" });
}

export async function likeMemoir(memoirId: string): Promise<Memoir> {
  return apiFetch<Memoir>(`/api/memoirs/${memoirId}/likes`, { method: "POST" });
}

export async function unlikeMemoir(memoirId: string): Promise<Memoir> {
  return apiFetch<Memoir>(`/api/memoirs/${memoirId}/likes`, { method: "DELETE" });
}

export async function listComments(memoirId: string): Promise<MemoirComment[]> {
  return apiFetch<MemoirComment[]>(`/api/memoirs/${memoirId}/comments`);
}

export async function createComment(memoirId: string, content: string): Promise<MemoirComment> {
  return apiFetch<MemoirComment>(`/api/memoirs/${memoirId}/comments`, {
    method: "POST",
    body: JSON.stringify({ content }),
  });
}

export async function updateComment(memoirId: string, commentId: string, content: string): Promise<MemoirComment> {
  return apiFetch<MemoirComment>(`/api/memoirs/${memoirId}/comments/${commentId}`, {
    method: "PUT",
    body: JSON.stringify({ content }),
  });
}

export async function deleteComment(memoirId: string, commentId: string): Promise<void> {
  return apiFetch<void>(`/api/memoirs/${memoirId}/comments/${commentId}`, { method: "DELETE" });
}
