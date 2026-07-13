import { useEffect, useRef, useState } from "react";
import { subscribeNotifications } from "../api/notifications";
import type { AppNotification } from "../types";
import { useAuth } from "../context/AuthContext";

export function NotificationBell() {
  const { user } = useAuth();
  const [items, setItems] = useState<AppNotification[]>([]);
  const [open, setOpen] = useState(false);
  const unreadCount = items.filter((n) => !n.read).length;
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!user) return;
    const unsubscribe = subscribeNotifications((notification) => {
      setItems((prev) => [notification, ...prev].slice(0, 20));
    });
    return unsubscribe;
  }, [user]);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  if (!user) return null;

  return (
    <div className="notification-bell" ref={wrapperRef}>
      <button
        type="button"
        className="icon-button"
        onClick={() => {
          setOpen((prev) => !prev);
          if (!open) setItems((prev) => prev.map((n) => ({ ...n, read: true })));
        }}
        aria-label="알림"
      >
        🔔
        {unreadCount > 0 && <span className="badge">{unreadCount}</span>}
      </button>
      {open && (
        <div className="notification-dropdown">
          {items.length === 0 ? (
            <p className="empty">알림이 없습니다.</p>
          ) : (
            <ul>
              {items.map((n) => (
                <li key={n.id}>
                  <p>{n.message}</p>
                  <time>{new Date(n.createdAt).toLocaleTimeString()}</time>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
