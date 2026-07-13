import type { Application, ApplicationStatus, Study } from "../types";
import { apiFetch, USE_MOCK } from "./client";
import { applications, delay, nextId, persist, requireUser, studies } from "./mockStore";

export interface StudyPayload {
  title: string;
  description: string;
  capacity: number;
  tagIds: string[];
}

export async function listStudies(): Promise<Study[]> {
  if (USE_MOCK) {
    await delay();
    return [...studies].sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }
  return apiFetch<Study[]>("/api/studies");
}

export async function getStudy(studyId: string): Promise<Study> {
  if (USE_MOCK) {
    await delay();
    const study = studies.find((s) => s.id === studyId);
    if (!study) throw new Error("스터디를 찾을 수 없습니다.");
    return study;
  }
  return apiFetch<Study>(`/api/studies/${studyId}`);
}

export async function createStudy(payload: StudyPayload): Promise<Study> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    const study: Study = {
      id: nextId(),
      title: payload.title,
      description: payload.description,
      capacity: payload.capacity,
      tagIds: payload.tagIds,
      ownerId: user.id,
      ownerName: user.name,
      memberCount: 1,
      createdAt: new Date().toISOString(),
    };
    studies.unshift(study);
    persist();
    return study;
  }
  return apiFetch<Study>("/api/studies", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function updateStudy(studyId: string, payload: StudyPayload): Promise<Study> {
  if (USE_MOCK) {
    await delay();
    const study = studies.find((s) => s.id === studyId);
    if (!study) throw new Error("스터디를 찾을 수 없습니다.");
    Object.assign(study, payload);
    persist();
    return study;
  }
  return apiFetch<Study>(`/api/studies/${studyId}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function deleteStudy(studyId: string): Promise<void> {
  if (USE_MOCK) {
    await delay();
    const index = studies.findIndex((s) => s.id === studyId);
    if (index >= 0) studies.splice(index, 1);
    persist();
    return;
  }
  return apiFetch<void>(`/api/studies/${studyId}`, { method: "DELETE" });
}

export async function applyToStudy(studyId: string): Promise<Application> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    const existing = applications.find(
      (a) => a.studyId === studyId && a.userId === user.id && a.status === "PENDING",
    );
    if (existing) return existing;
    const application: Application = {
      id: nextId(),
      studyId,
      userId: user.id,
      userName: user.name,
      status: "PENDING",
      appliedAt: new Date().toISOString(),
    };
    applications.push(application);
    persist();
    return application;
  }
  return apiFetch<Application>(`/api/studies/${studyId}/applications`, { method: "POST" });
}

export async function cancelApplication(studyId: string): Promise<void> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    const index = applications.findIndex(
      (a) => a.studyId === studyId && a.userId === user.id && a.status === "PENDING",
    );
    if (index >= 0) applications.splice(index, 1);
    persist();
    return;
  }
  return apiFetch<void>(`/api/studies/${studyId}/applications`, { method: "DELETE" });
}

export async function listApplications(studyId: string): Promise<Application[]> {
  if (USE_MOCK) {
    await delay();
    return applications.filter((a) => a.studyId === studyId);
  }
  return apiFetch<Application[]>(`/api/studies/${studyId}/applications`);
}

export async function decideApplication(
  studyId: string,
  appId: string,
  status: Extract<ApplicationStatus, "APPROVED" | "REJECTED">,
): Promise<Application> {
  if (USE_MOCK) {
    await delay();
    const application = applications.find((a) => a.id === appId && a.studyId === studyId);
    if (!application) throw new Error("신청 내역을 찾을 수 없습니다.");
    application.status = status;
    if (status === "APPROVED") {
      const study = studies.find((s) => s.id === studyId);
      if (study) study.memberCount += 1;
    }
    persist();
    return application;
  }
  return apiFetch<Application>(`/api/studies/${studyId}/applications/${appId}`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}
