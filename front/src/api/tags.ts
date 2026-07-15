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

export async function listMyTagIds(): Promise<number[]> {
  if (USE_MOCK) {
    await delay(100);
    const user = requireUser();
    return getMyTagIds(user.id);
  }
  const myTags = await apiFetch<Tag[]>("/api/tags/me");
  return myTags.map((tag) => tag.id);
}

// 선호 태그를 서버에 저장. 매칭(matchStudies)은 tagIds를 안 주면 이 저장된 태그를 기준으로 계산된다.
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

// tagIds를 주면 저장 없이 즉석으로 해당 태그 기준 매칭 결과를 미리 볼 수 있다.
export async function matchStudies(tagIds?: number[]): Promise<StudyMatch[]> {
  const previewTagIds = tagIds && tagIds.length > 0 ? tagIds : undefined;

  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    const myTagIds = previewTagIds ?? getMyTagIds(user.id);
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

  const query = previewTagIds ? `?${previewTagIds.map((id) => `tagIds=${id}`).join("&")}` : "";
  return apiFetch<StudyMatch[]>(`/api/studies/match${query}`);
}
