import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <div className="page center-text">
      <h1>404</h1>
      <p>페이지를 찾을 수 없습니다.</p>
      <Link to="/studies">스터디 목록으로</Link>
    </div>
  );
}
