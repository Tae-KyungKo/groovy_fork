import type { AppNotification } from "../types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

// GET /api/notifications/stream (SSE) 구독.
export function subscribeNotifications(onMessage: (notification: AppNotification) => void) {
  const source = new EventSource(`${BASE_URL}/api/notifications/stream`, {
    withCredentials: true,
  });
  source.onmessage = (event) => {
    const data = JSON.parse(event.data) as AppNotification;
    onMessage(data);
  };
  return () => source.close();
}
