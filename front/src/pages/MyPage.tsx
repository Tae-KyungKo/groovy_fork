import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { cancelApplication } from "../api/studies";
import { listMyTagIds, listTags, saveMyTags } from "../api/tags";
import { getMyApplications, getMyStudies } from "../api/users";
import { StudyCard } from "../components/StudyCard";
import { TagPicker } from "../components/TagPicker";
import { useAuth } from "../context/AuthContext";
import type { MyApplication, Study, Tag } from "../types";

const STATUS_LABELS: Record<MyApplication["status"], string> = {
  PENDING: "대기중",
  APPROVED: "승인됨",
  REJECTED: "거절됨",
};

export function MyPage() {
  const { user } = useAuth();

  const [tags, setTags] = useState<Tag[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);
  const [savingTags, setSavingTags] = useState(false);

  const [myStudies, setMyStudies] = useState<Study[]>([]);
  const [applications, setApplications] = useState<MyApplication[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) return;
    Promise.all([listTags(), listMyTagIds(), getMyStudies(), getMyApplications()]).then(
      ([tagList, myTagIds, studies, myApplications]) => {
        setTags(tagList);
        setSelectedTagIds(myTagIds);
        setMyStudies(studies);
        setApplications(myApplications);
        setLoading(false);
      },
    );
  }, [user]);

  const tagsById = useMemo(() => new Map(tags.map((t) => [t.id, t])), [tags]);
  const joinedStudies = applications.filter((app) => app.status === "APPROVED");
  const otherApplications = applications.filter((app) => app.status !== "APPROVED");

  function toggleTag(tagId: number) {
    setSelectedTagIds((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    );
  }

  async function handleSaveTags() {
    setSavingTags(true);
    try {
      await saveMyTags(selectedTagIds);
    } finally {
      setSavingTags(false);
    }
  }

  async function handleCancel(studyId: string) {
    await cancelApplication(studyId);
    setApplications((prev) => prev.filter((app) => app.studyId !== studyId));
  }

  if (!user) return null;
  if (loading) return <p className="page-loading">불러오는 중...</p>;

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

      <div className="card">
        <h2>관심 태그</h2>
        <TagPicker tags={tags} selected={selectedTagIds} onToggle={toggleTag} />
        <button type="button" onClick={handleSaveTags} disabled={savingTags}>
          {savingTags ? "저장 중..." : "저장"}
        </button>
      </div>

      <div className="card">
        <h2>내가 만든 스터디</h2>
        {myStudies.length === 0 ? (
          <p className="empty">만든 스터디가 없습니다.</p>
        ) : (
          <div className="study-grid">
            {myStudies.map((study) => (
              <StudyCard key={study.id} study={study} tagsById={tagsById} />
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <h2>참여 중인 스터디</h2>
        {joinedStudies.length === 0 ? (
          <p className="empty">참여 중인 스터디가 없습니다.</p>
        ) : (
          <ul className="list">
            {joinedStudies.map((app) => (
              <li key={app.id} className="card list-item">
                <Link to={`/studies/${app.studyId}`}>{app.studyTitle}</Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h2>신청 내역</h2>
        {otherApplications.length === 0 ? (
          <p className="empty">대기중이거나 거절된 신청이 없습니다.</p>
        ) : (
          <ul className="list">
            {otherApplications.map((app) => (
              <li key={app.id} className="card list-item">
                <div>
                  <Link to={`/studies/${app.studyId}`}>{app.studyTitle}</Link>
                  <time> · {new Date(app.appliedAt).toLocaleDateString()}</time>
                </div>
                <div className="button-row">
                  <span className={`status status-${app.status.toLowerCase()}`}>
                    {STATUS_LABELS[app.status]}
                  </span>
                  {app.status === "PENDING" && (
                    <button type="button" className="secondary" onClick={() => handleCancel(app.studyId)}>
                      신청 취소
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
