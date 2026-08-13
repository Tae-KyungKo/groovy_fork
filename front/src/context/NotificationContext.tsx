import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { BASE_URL } from "../api/client";
import { issueSubscribeTicket, listNotifications, markAllNotificationsRead, markNotificationRead } from "../api/notifications";
import type { Notification } from "../types";
import { useAuth } from "./AuthContext";

interface NotificationContextValue {
  notifications: Notification[];
  markRead: (id: string) => Promise<void>;
  markAllRead: () => Promise<void>;
}

const NotificationContext = createContext<NotificationContextValue | null>(null);

const RECONNECT_DELAY_MS = 3000;

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!user) {
      setNotifications([]);
      return;
    }

    let cancelled = false;

    listNotifications().then((list) => {
      if (!cancelled) setNotifications(list);
    });

    async function connect() {
      try {
        const ticket = await issueSubscribeTicket();
        if (cancelled) return;

        const eventSource = new EventSource(`${BASE_URL}/api/notifications/subscribe?ticket=${ticket}`);
        eventSource.addEventListener("notification", (event) => {
          const notification = JSON.parse((event as MessageEvent).data) as Notification;
          setNotifications((prev) => [notification, ...prev]);
        });
        // 티켓이 1회용이라 EventSource 기본 재연결(같은 URL 재시도)은 항상 실패한다.
        // 연결을 확실히 닫고, 새 티켓을 다시 발급받아 우리가 직접 재연결한다.
        eventSource.onerror = () => {
          eventSource.close();
          if (!cancelled) {
            reconnectTimerRef.current = setTimeout(connect, RECONNECT_DELAY_MS);
          }
        };
        eventSourceRef.current = eventSource;
      } catch {
        if (!cancelled) {
          reconnectTimerRef.current = setTimeout(connect, RECONNECT_DELAY_MS);
        }
      }
    }

    connect();

    return () => {
      cancelled = true;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
    };
  }, [user]);

  const markRead = useCallback(async (id: string) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
    await markNotificationRead(id);
  }, []);

  const markAllRead = useCallback(async () => {
    setNotifications([]);
    await markAllNotificationsRead();
  }, []);

  const value = { notifications, markRead, markAllRead };

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
}

export function useNotifications() {
  const ctx = useContext(NotificationContext);
  if (!ctx) throw new Error("useNotifications는 NotificationProvider 내부에서만 사용할 수 있습니다.");
  return ctx;
}
