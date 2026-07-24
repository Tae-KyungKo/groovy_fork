import type { WaitingPosition } from "../types";
import { apiFetch, USE_MOCK } from "./client";
import { delay, persist, requireUser, waitingList } from "./mockStore";

export async function joinWaiting(studyId: string): Promise<WaitingPosition> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    const list = (waitingList[studyId] ??= []);
    if (!list.includes(user.id)) list.push(user.id);
    persist();
    return { position: list.indexOf(user.id) + 1, totalWaiting: list.length };
  }
  return apiFetch<WaitingPosition>(`/api/studies/${studyId}/waiting`, { method: "POST" });
}

export async function getWaitingPosition(studyId: string): Promise<WaitingPosition | null> {
  if (USE_MOCK) {
    await delay(150);
    const user = requireUser();
    const list = waitingList[studyId] ?? [];
    const index = list.indexOf(user.id);
    if (index < 0) return null;
    return { position: index + 1, totalWaiting: list.length };
  }
  return apiFetch<WaitingPosition | null>(`/api/studies/${studyId}/waiting/position`);
}

export async function leaveWaiting(studyId: string): Promise<void> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    const list = waitingList[studyId];
    if (!list) return;
    const index = list.indexOf(user.id);
    if (index >= 0) list.splice(index, 1);
    persist();
    return;
  }
  return apiFetch<void>(`/api/studies/${studyId}/waiting`, { method: "DELETE" });
}
