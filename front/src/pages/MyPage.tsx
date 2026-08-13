import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { cancelApplication } from "../api/studies";
import { listMyTagIds, listTags, saveMyTags } from "../api/tags";
import { getMyApplications, getMyStudies } from "../api/users";
import { TagPicker } from "../components/TagPicker";
import { useAuth } from "../context/AuthContext";
import type { MyApplication, Study, Tag } from "../types";
import { DAY_LABELS } from "../types";
import { formatTime } from "../utils/date";

const STATUS_LABELS: Record<MyApplication["status"], string> = {
  PENDING: "승인 대기",
  APPROVED: "참여 중",
  REJECTED: "거절됨",
};

export function MyPage() {
  const { user } = useAuth();

  const [tags, setTags] = useState<Tag[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);
  const [savingTags, setSavingTags] = useState(false);
  const [tagError, setTagError] = useState<string | null>(null);

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

  function toggleTag(tagId: number) {
    setSelectedTagIds((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    );
  }

  async function handleSaveTags() {
    setSavingTags(true);
    setTagError(null);
    try {
      await saveMyTags(selectedTagIds);
    } catch (err) {
      setTagError(err instanceof Error ? err.message : "태그 저장에 실패했습니다.");
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
    <div className="page me-main">
      <h1>마이페이지</h1>

      <div className="card profile-card">
        <span className="avatar avatar-lg">{user.name.charAt(0)}</span>
        <div className="profile-info">
          <p className="profile-name">{user.name}</p>
          <p className="profile-email">{user.email}</p>
        </div>
      </div>

      <div className="card">
        <div className="section-head">
          <h2>관심 태그</h2>
          <button type="button" className="secondary small" onClick={handleSaveTags} disabled={savingTags}>
            {savingTags ? "저장 중..." : "저장"}
          </button>
        </div>
        <TagPicker tags={tags} selected={selectedTagIds} onToggle={toggleTag} />
        {tagError && <p className="error">{tagError}</p>}
      </div>

      <div className="card">
        <div className="section-head">
          <h2>내가 만든 스터디</h2>
          <Link to="/studies/new" className="button secondary small">
            새 스터디 만들기
          </Link>
        </div>
        {myStudies.length === 0 ? (
          <p className="empty">만든 스터디가 없습니다.</p>
        ) : (
          <div className="me-rows">
            {myStudies.map((study) => {
              const isFull = study.memberCount >= study.capacity;
              const meta = [
                study.meetingDays.length
                  ? study.meetingDays.map((day) => DAY_LABELS[day]).join(", ")
                  : null,
                study.meetingStartTime && study.meetingEndTime
                  ? `${formatTime(study.meetingStartTime)} - ${formatTime(study.meetingEndTime)}`
                  : null,
                `${study.memberCount}/${study.capacity}명`,
              ]
                .filter(Boolean)
                .join(" · ");
              return (
                <div key={study.id} className="me-row">
                  <Link to={`/studies/${study.id}`} className="me-row-main">
                    <p className="me-row-title">{study.title}</p>
                    <p className="me-row-meta">{meta}</p>
                  </Link>
                  <span className={`status ${isFull ? "status-closed" : "status-open"}`}>
                    {isFull ? "모집마감" : "모집중"}
                  </span>
                  <Link to={`/studies/${study.id}/applications`} className="pill-action">
                    신청자 관리
                  </Link>
                  <Link to={`/studies/${study.id}/edit`} className="button secondary small">
                    수정
                  </Link>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="card">
        <div className="section-head">
          <h2>참여 중인 스터디 · 신청 내역</h2>
        </div>
        {applications.length === 0 ? (
          <p className="empty">참여 중이거나 신청한 스터디가 없습니다.</p>
        ) : (
          <div className="me-rows">
            {applications.map((app) => (
              <div key={app.id} className="me-row">
                <Link to={`/studies/${app.studyId}`} className="me-row-main">
                  <p className="me-row-title">{app.studyTitle}</p>
                  <p className="me-row-meta">
                    신청일 {new Date(app.appliedAt).toLocaleDateString()}
                  </p>
                </Link>
                <span className={`status status-${app.status.toLowerCase()}`}>
                  {STATUS_LABELS[app.status]}
                </span>
                {app.status === "PENDING" && (
                  <button type="button" className="cancel-link" onClick={() => handleCancel(app.studyId)}>
                    신청 취소
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
