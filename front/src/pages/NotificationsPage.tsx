import { useNavigate } from "react-router-dom";
import { BellIcon, CalendarIcon, CheckIcon, UsersIcon, XIcon } from "../components/icons";
import { useNotifications } from "../context/NotificationContext";
import type { Notification, NotificationType } from "../types";
import { formatRelativeTime } from "../utils/date";

const ICON_BY_TYPE: Record<NotificationType, { Icon: typeof BellIcon; className: string }> = {
  APPLICATION_RECEIVED: { Icon: UsersIcon, className: "type-received" },
  APPLICATION_APPROVED: { Icon: CheckIcon, className: "type-approved" },
  APPLICATION_REJECTED: { Icon: XIcon, className: "type-rejected" },
  STUDY_SCHEDULE_CHANGED: { Icon: CalendarIcon, className: "type-schedule" },
  WAITLIST_SEAT_OPENED: { Icon: BellIcon, className: "type-waitlist" },
};

function resolveTarget(notification: Notification): string | null {
  switch (notification.type) {
    case "APPLICATION_RECEIVED":
      return notification.targetStudyId ? `/studies/${notification.targetStudyId}/applications` : null;
    case "APPLICATION_APPROVED":
    case "APPLICATION_REJECTED":
    case "WAITLIST_SEAT_OPENED":
      return notification.targetStudyId ? `/studies/${notification.targetStudyId}` : null;
    case "STUDY_SCHEDULE_CHANGED":
      return "/calendar";
    default:
      return null;
  }
}

export function NotificationsPage() {
  const { notifications, markRead, markAllRead } = useNotifications();
  const navigate = useNavigate();

  async function handleClick(notification: Notification) {
    const target = resolveTarget(notification);
    await markRead(notification.id);
    if (target) navigate(target);
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>알림함</h1>
          <p className="page-subtitle">스터디 신청 결과와 새 신청 소식을 확인해요</p>
        </div>
        {notifications.length > 0 && (
          <button type="button" className="secondary small" onClick={() => markAllRead()}>
            모두 읽음 처리
          </button>
        )}
      </div>

      {notifications.length === 0 ? (
        <div className="side-empty">
          <p className="side-empty-title">알림이 없어요</p>
          <p className="side-empty-desc">새로운 소식이 오면 여기에 표시돼요</p>
        </div>
      ) : (
        <ul className="notification-list">
          {notifications.map((notification) => {
            const { Icon, className } = ICON_BY_TYPE[notification.type];
            return (
              <li key={notification.id}>
                <button type="button" className="notification-card" onClick={() => handleClick(notification)}>
                  <span className={`notification-icon ${className}`}>
                    <Icon size={18} />
                  </span>
                  <span className="notification-body">
                    <span className="notification-title">{notification.title}</span>
                    <span className="notification-message">{notification.message}</span>
                    <span className="notification-time">{formatRelativeTime(notification.createdAt)}</span>
                  </span>
                  <span className="notification-dot" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
