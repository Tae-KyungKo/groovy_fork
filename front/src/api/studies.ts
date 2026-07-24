import type { Application, ApplicationStatus, DayOfWeek, PageResponse, Study } from "../types";
import { apiFetch } from "./client";

export interface StudyPayload {
  title: string;
  description: string;
  capacity: number;
  tagIds: number[];
  meetingDays: DayOfWeek[];
  // 백엔드에서 선택 항목(null 허용)이라 비워두면 아예 보내지 않는다.
  meetingStartTime?: string;
  meetingEndTime?: string;
}

export interface StudyListResult {
  studies: Study[];
  page: number;
  totalPages: number;
}

export async function listStudies(page = 0): Promise<StudyListResult> {
  const result = await apiFetch<PageResponse<Study>>(`/api/studies?page=${page}`);
  return { studies: result.content, page: result.number, totalPages: result.totalPages };
}

export async function getStudy(studyId: string): Promise<Study> {
  return apiFetch<Study>(`/api/studies/${studyId}`);
}

export async function createStudy(payload: StudyPayload): Promise<Study> {
  return apiFetch<Study>("/api/studies", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function updateStudy(studyId: string, payload: StudyPayload): Promise<Study> {
  return apiFetch<Study>(`/api/studies/${studyId}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function deleteStudy(studyId: string): Promise<void> {
  return apiFetch<void>(`/api/studies/${studyId}`, { method: "DELETE" });
}

export async function applyToStudy(studyId: string): Promise<Application> {
  return apiFetch<Application>(`/api/studies/${studyId}/applications`, { method: "POST" });
}

export async function cancelApplication(studyId: string): Promise<void> {
  return apiFetch<void>(`/api/studies/${studyId}/applications`, { method: "DELETE" });
}

export async function listApplications(studyId: string): Promise<Application[]> {
  return apiFetch<Application[]>(`/api/studies/${studyId}/applications`);
}

export async function decideApplication(
  studyId: string,
  appId: string,
  status: Extract<ApplicationStatus, "APPROVED" | "REJECTED">,
): Promise<Application> {
  return apiFetch<Application>(`/api/studies/${studyId}/applications/${appId}`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}
