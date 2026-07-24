import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { decideApplication, listApplications } from "../api/studies";
import type { Application } from "../types";

export function StudyApplicationsPage() {
  const { studyId } = useParams();
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!studyId) return;
    listApplications(studyId).then((list) => {
      setApplications(list);
      setLoading(false);
    });
  }, [studyId]);

  async function handleDecide(appId: string, status: "APPROVED" | "REJECTED") {
    if (!studyId) return;
    const updated = await decideApplication(studyId, appId, status);
    setApplications((prev) => prev.map((a) => (a.id === appId ? updated : a)));
  }

  if (loading) return <p className="page-loading">불러오는 중...</p>;

  return (
    <div className="page">
      <h1>참여 신청 관리</h1>
      {applications.length === 0 ? (
        <p className="empty">신청 내역이 없습니다.</p>
      ) : (
        <ul className="list">
          {applications.map((app) => (
            <li key={app.id} className="card list-item">
              <div>
                <p className="strong">{app.userName}</p>
                <time>{new Date(app.appliedAt).toLocaleString()}</time>
              </div>
              {app.status === "PENDING" ? (
                <div className="button-row">
                  <button type="button" onClick={() => handleDecide(app.id, "APPROVED")}>
                    승인
                  </button>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => handleDecide(app.id, "REJECTED")}
                  >
                    거절
                  </button>
                </div>
              ) : (
                <span className={`status status-${app.status.toLowerCase()}`}>
                  {app.status === "APPROVED" ? "승인됨" : "거절됨"}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
