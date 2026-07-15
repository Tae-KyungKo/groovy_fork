import type { MyApplication, Study } from "../types";
import { apiFetch, USE_MOCK } from "./client";
import { applications, delay, requireUser, studies } from "./mockStore";

// 마이페이지 "내가 만든 스터디" 목록.
export async function getMyStudies(): Promise<Study[]> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    return studies.filter((study) => study.leaderId === user.id);
  }
  return apiFetch<Study[]>("/api/users/me/studies");
}

// 마이페이지 "참여 중인 스터디 / 신청 내역" 목록.
export async function getMyApplications(): Promise<MyApplication[]> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    return applications
      .filter((app) => app.userId === user.id)
      .map((app) => ({
        id: app.id,
        studyId: app.studyId,
        studyTitle: studies.find((study) => study.id === app.studyId)?.title ?? "삭제된 스터디",
        status: app.status,
        appliedAt: app.appliedAt,
      }));
  }
  return apiFetch<MyApplication[]>("/api/users/me/applications");
}
