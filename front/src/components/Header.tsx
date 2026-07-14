import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { NotificationBell } from "./NotificationBell";

export function Header() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

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
        <Link to="/studies">스터디</Link>
        {user && <Link to="/calendar">캘린더</Link>}
      </nav>
      <div className="header-actions">
        {user ? (
          <>
            <NotificationBell />
            <Link to="/me">{user.name}</Link>
            <button type="button" onClick={handleLogout}>
              로그아웃
            </button>
          </>
        ) : (
          <>
            <Link to="/login">로그인</Link>
            <Link to="/signup">회원가입</Link>
          </>
        )}
      </div>
    </header>
  );
}
