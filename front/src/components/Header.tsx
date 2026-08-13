import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useNotifications } from "../context/NotificationContext";
import { BellIcon } from "./icons";

export function Header() {
  const { user, logout } = useAuth();
  const { notifications } = useNotifications();
  const navigate = useNavigate();
  const unreadCount = notifications.length;

  async function handleLogout() {
    await logout();
    navigate("/login");
  }

  return (
    <header className="app-header">
      <Link to="/studies" className="brand">
        Groovy
      </Link>
      <nav>
        <NavLink to="/studies">스터디</NavLink>
        <NavLink to="/memoirs">회고록</NavLink>
        {user && <NavLink to="/calendar">캘린더</NavLink>}
      </nav>
      <div className="header-actions">
        {user ? (
          <>
            <Link to="/notifications" className="icon-button notification-bell" aria-label="알림함">
              <BellIcon />
              {unreadCount > 0 && (
                <span className="notification-badge">{unreadCount > 9 ? "9+" : unreadCount}</span>
              )}
            </Link>
            <Link to="/me">{user.name}</Link>
            <button type="button" className="secondary" onClick={handleLogout}>
              로그아웃
            </button>
          </>
        ) : (
          <>
            <Link to="/login">로그인</Link>
            <Link to="/signup" className="button">
              회원가입
            </Link>
          </>
        )}
      </div>
    </header>
  );
}
