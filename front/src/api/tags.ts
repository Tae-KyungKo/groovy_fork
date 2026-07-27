import type { PageResponse, StudyMatch, Tag } from "../types";
import { apiFetch } from "./client";

export interface StudyMatchResult {
  matches: StudyMatch[];
  page: number;
  totalPages: number;
}

export async function listTags(): Promise<Tag[]> {
  return apiFetch<Tag[]>("/api/tags");
}

export async function listMyTagIds(): Promise<number[]> {
  const myTags = await apiFetch<Tag[]>("/api/tags/me");
  return myTags.map((tag) => tag.id);
}

// 선호 태그를 서버에 저장. 매칭(matchStudies)은 tagIds를 안 주면 이 저장된 태그를 기준으로 계산된다.
export async function saveMyTags(tagIds: number[]): Promise<void> {
  return apiFetch<void>("/api/tags/me", {
    method: "PUT",
    body: JSON.stringify({ tagIds }),
  });
}

// tagIds를 주면 저장 없이 즉석으로 해당 태그 기준 매칭 결과를 미리 볼 수 있다.
export async function matchStudies(tagIds?: number[], page = 0): Promise<StudyMatchResult> {
  const previewTagIds = tagIds && tagIds.length > 0 ? tagIds : undefined;
  const params = new URLSearchParams({ page: String(page) });
  previewTagIds?.forEach((id) => params.append("tagIds", String(id)));

  const result = await apiFetch<PageResponse<StudyMatch>>(`/api/studies/match?${params.toString()}`);
  return { matches: result.content, page: result.number, totalPages: result.totalPages };
}
