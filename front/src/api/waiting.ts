import type { WaitingPosition } from "../types";
import { apiFetch } from "./client";

export async function joinWaiting(studyId: string): Promise<WaitingPosition> {
  return apiFetch<WaitingPosition>(`/api/studies/${studyId}/waiting`, { method: "POST" });
}

export async function getWaitingPosition(studyId: string): Promise<WaitingPosition | null> {
  return apiFetch<WaitingPosition | null>(`/api/studies/${studyId}/waiting/position`);
}

export async function leaveWaiting(studyId: string): Promise<void> {
  return apiFetch<void>(`/api/studies/${studyId}/waiting`, { method: "DELETE" });
}
