import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { applyToStudy, cancelApplication, deleteStudy, getStudy } from "../api/studies";
import { listTags } from "../api/tags";
import { getWaitingPosition, joinWaiting, leaveWaiting } from "../api/waiting";
import { CalendarIcon, ChevronLeftIcon, ClockIcon, UsersIcon } from "../components/icons";
import { useAuth } from "../context/AuthContext";
import type { Study, Tag, WaitingPosition } from "../types";
import { DAY_LABELS } from "../types";
import { formatTime } from "../utils/date";

type ApplyState = "NONE" | "PENDING";

export function StudyDetailPage() {
  const { studyId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [study, setStudy] = useState<Study | null>(null);
  const [tags, setTags] = useState<Tag[]>([]);
  const [applyState, setApplyState] = useState<ApplyState>("NONE");
  const [waiting, setWaiting] = useState<WaitingPosition | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);

  const isOwner = user && study && user.id === study.leaderId;
  const isFull = study ? study.memberCount >= study.capacity : false;

  useEffect(() => {
    if (!studyId) return;
    setLoading(true);
    Promise.all([getStudy(studyId), listTags()]).then(([studyData, tagList]) => {
      setStudy(studyData);
      setTags(tagList);
      setLoading(false);
    });
    if (user) {
      getWaitingPosition(studyId).then(setWaiting);
    }
  }, [studyId, user]);

  const tagsById = useMemo(() => new Map(tags.map((t) => [t.id, t])), [tags]);

  async function handleApply() {
    if (!studyId) return;
    setActionError(null);
    try {
      await applyToStudy(studyId);
      setApplyState("PENDING");
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "신청에 실패했습니다.");
    }
  }

  async function handleCancelApply() {
    if (!studyId) return;
    await cancelApplication(studyId);
    setApplyState("NONE");
  }

  async function handleJoinWaiting() {
    if (!studyId) return;
    const position = await joinWaiting(studyId);
    setWaiting(position);
  }

  async function handleLeaveWaiting() {
    if (!studyId) return;
    await leaveWaiting(studyId);
    setWaiting(null);
  }

  async function handleDelete() {
    if (!studyId) return;
    if (!confirm("정말 삭제할까요?")) return;
    await deleteStudy(studyId);
    navigate("/studies");
  }

  if (loading) return <p className="page-loading">불러오는 중...</p>;
  if (!study) return <p className="empty">스터디를 찾을 수 없습니다.</p>;

  const meetingDaysText = study.meetingDays.length
    ? study.meetingDays.map((day) => DAY_LABELS[day]).join(", ")
    : "미정";
  const meetingTimeText =
    study.meetingStartTime && study.meetingEndTime
      ? `${formatTime(study.meetingStartTime)} - ${formatTime(study.meetingEndTime)}`
      : "미정";

  return (
    <div className="page detail-main">
      <Link to="/studies" className="back-link">
        <ChevronLeftIcon size={14} />
        스터디 목록
      </Link>

      <div>
        <div className="tag-picker detail-tags">
          {study.tagIds.map((id) => (
            <span key={id} className="tag-chip">
              #{tagsById.get(id)?.name ?? id}
            </span>
          ))}
        </div>
        <h1 className="detail-title">{study.title}</h1>
        <div className="host-row">
          <span className="avatar avatar-sm">{study.leaderName.charAt(0)}</span>
          <span className="host-name">{study.leaderName}</span>
          <span className="host-badge">방장</span>
        </div>
      </div>

      <div className="card meta-grid">
        <div className="meta-cell">
          <UsersIcon />
          <div>
            <div className="meta-label">인원</div>
            <div className="meta-value">
              {study.memberCount}/{study.capacity}명
            </div>
          </div>
        </div>
        <div className="meta-cell">
          <CalendarIcon />
          <div>
            <div className="meta-label">모임 요일</div>
            <div className="meta-value">{meetingDaysText}</div>
          </div>
        </div>
        <div className="meta-cell">
          <ClockIcon />
          <div>
            <div className="meta-label">시간</div>
            <div className="meta-value">{meetingTimeText}</div>
          </div>
        </div>
      </div>

      <section className="section-block">
        <h2 className="section-title">스터디 소개</h2>
        <p className="desc-text">{study.description}</p>
      </section>

      <div className="action-bar">
        {!isOwner && user && (
          <>
            {applyState === "NONE" ? (
              <button type="button" className="wide" onClick={handleApply} disabled={isFull}>
                {isFull ? "정원 마감" : "참여 신청하기"}
              </button>
            ) : (
              <button type="button" className="wide secondary" onClick={handleCancelApply}>
                신청 취소
              </button>
            )}

            {isFull &&
              (waiting ? (
                <button type="button" className="wide secondary" onClick={handleLeaveWaiting}>
                  대기 취소 (내 순번 {waiting.position}/{waiting.totalWaiting})
                </button>
              ) : (
                <button type="button" className="wide secondary" onClick={handleJoinWaiting}>
                  빈자리 대기 신청
                </button>
              ))}
          </>
        )}
        {!user && <p className="host-hint">참여 신청은 로그인 후 이용할 수 있습니다.</p>}
        {isOwner && (
          <p className="host-hint">
            방장이신가요? <Link to={`/studies/${study.id}/applications`}>신청자 관리</Link> ·{" "}
            <Link to={`/studies/${study.id}/edit`}>스터디 수정</Link> ·{" "}
            <button type="button" className="link-danger" onClick={handleDelete}>
              삭제
            </button>
          </p>
        )}
        {actionError && <p className="error">{actionError}</p>}
      </div>
    </div>
  );
}
