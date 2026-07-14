import type { StudyMatch, Tag } from "../types";
import { apiFetch, USE_MOCK } from "./client";
import { delay, getMyTagIds, persist, requireUser, setMyTagIds, studies, tags } from "./mockStore";

export async function listTags(): Promise<Tag[]> {
  if (USE_MOCK) {
    await delay(150);
    return tags;
  }
  return apiFetch<Tag[]>("/api/tags");
}

// 선호 태그를 서버에 저장. 매칭(matchStudies)은 이 저장된 태그를 기준으로 계산된다.
export async function saveMyTags(tagIds: number[]): Promise<void> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    setMyTagIds(user.id, tagIds);
    persist();
    return;
  }
  return apiFetch<void>("/api/tags/me", {
    method: "PUT",
    body: JSON.stringify({ tagIds }),
  });
}

export async function matchStudies(): Promise<StudyMatch[]> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    const myTagIds = getMyTagIds(user.id);
    if (myTagIds.length === 0) {
      return studies.map((study) => ({ study, matchedTagCount: 0, matchScore: 0 }));
    }
    return studies
      .map((study) => {
        const matchedTagCount = study.tagIds.filter((id) => myTagIds.includes(id)).length;
        return {
          study,
          matchedTagCount,
          matchScore: Math.round((matchedTagCount / myTagIds.length) * 100),
        };
      })
      .sort((a, b) => b.matchScore - a.matchScore);
  }
  return apiFetch<StudyMatch[]>("/api/studies/match");
}
