import type { Notification } from "../types";
import { apiFetch } from "./client";

export async function listNotifications(): Promise<Notification[]> {
  return apiFetch<Notification[]>("/api/notifications");
}

export async function markNotificationRead(id: string): Promise<void> {
  return apiFetch<void>(`/api/notifications/${id}/read`, { method: "POST" });
}

export async function markAllNotificationsRead(): Promise<void> {
  return apiFetch<void>("/api/notifications/read-all", { method: "POST" });
}

export async function issueSubscribeTicket(): Promise<string> {
  const result = await apiFetch<{ ticket: string }>("/api/notifications/subscribe-ticket", { method: "POST" });
  return result.ticket;
}
