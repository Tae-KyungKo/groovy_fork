import { useAuth } from "../context/AuthContext";

export function MyPage() {
  const { user } = useAuth();
  if (!user) return null;

  return (
    <div className="page">
      <h1>마이페이지</h1>
      <div className="card">
        <dl className="detail-list">
          <dt>이름</dt>
          <dd>{user.name}</dd>
          <dt>이메일</dt>
          <dd>{user.email}</dd>
        </dl>
      </div>
    </div>
  );
}
