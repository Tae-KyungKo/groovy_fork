import type { AppNotification } from "../types";
import { USE_MOCK } from "./client";
import { nextId } from "./mockStore";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

// GET /api/notifications/stream (SSE) 구독. mock 모드에서는 데모용으로
// 일정 주기마다 가짜 알림을 흘려보냅니다.
export function subscribeNotifications(onMessage: (notification: AppNotification) => void) {
  if (USE_MOCK) {
    const messages = [
      "스터디 참여 신청이 승인되었습니다.",
      "대기 순번이 한 칸 당겨졌습니다.",
      "새로운 참여 신청이 도착했습니다.",
    ];
    let i = 0;
    const timer = setInterval(() => {
      onMessage({
        id: nextId(),
        message: messages[i % messages.length],
        createdAt: new Date().toISOString(),
        read: false,
      });
      i += 1;
    }, 15000);
    return () => clearInterval(timer);
  }

  const source = new EventSource(`${BASE_URL}/api/notifications/stream`, {
    withCredentials: true,
  });
  source.onmessage = (event) => {
    const data = JSON.parse(event.data) as AppNotification;
    onMessage(data);
  };
  return () => source.close();
}
