import type { StudyMatch, Tag } from "../types";
import { apiFetch, USE_MOCK } from "./client";
import { delay, studies, tags } from "./mockStore";

export async function listTags(): Promise<Tag[]> {
  if (USE_MOCK) {
    await delay(150);
    return tags;
  }
  return apiFetch<Tag[]>("/api/tags");
}

export async function matchStudies(tagIds: string[]): Promise<StudyMatch[]> {
  if (USE_MOCK) {
    await delay();
    if (tagIds.length === 0) {
      return studies.map((s) => ({ ...s, matchScore: 0 }));
    }
    return studies
      .map((s) => {
        const overlap = s.tagIds.filter((id) => tagIds.includes(id)).length;
        return { ...s, matchScore: Math.round((overlap / tagIds.length) * 100) };
      })
      .sort((a, b) => b.matchScore - a.matchScore);
  }
  const query = new URLSearchParams({ tagIds: tagIds.join(",") });
  return apiFetch<StudyMatch[]>(`/api/studies/match?${query.toString()}`);
}
