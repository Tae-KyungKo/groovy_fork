import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  applyToStudy,
  cancelApplication,
  cancelWaitlist,
  deleteStudy,
  getStudy,
  leaveStudy,
  registerWaitlist,
} from "../api/studies";
import { listTags } from "../api/tags";
import { CalendarIcon, ChevronLeftIcon, ClockIcon, UsersIcon } from "../components/icons";
import { useAuth } from "../context/AuthContext";
import type { ApplicationStatus, Study, Tag } from "../types";
import { DAY_LABELS } from "../types";
import { formatTime } from "../utils/date";

type MyApplicationStatus = ApplicationStatus | "NONE";

export function StudyDetailPage() {
  const { studyId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [study, setStudy] = useState<Study | null>(null);
  const [tags, setTags] = useState<Tag[]>([]);
  const [applyStatus, setApplyStatus] = useState<MyApplicationStatus>("NONE");
  const [waitlistRegistered, setWaitlistRegistered] = useState(false);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionPending, setActionPending] = useState(false);

  const isOwner = user && study && user.id === study.leaderId;
  const isFull = study ? study.memberCount >= study.capacity : false;

  useEffect(() => {
    if (!studyId) return;
    setLoading(true);
    Promise.all([getStudy(studyId), listTags()]).then(([studyData, tagList]) => {
      setStudy(studyData);
      setApplyStatus(studyData.myApplicationStatus);
      setWaitlistRegistered(studyData.myWaitlistRegistered);
      setTags(tagList);
      setLoading(false);
    });
  }, [studyId]);

  const tagsById = useMemo(() => new Map(tags.map((t) => [t.id, t])), [tags]);

  async function handleApply() {
    if (!studyId) return;
    setActionError(null);
    setActionPending(true);
    try {
      await applyToStudy(studyId);
      setApplyStatus("PENDING");
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "신청에 실패했습니다.");
    } finally {
      setActionPending(false);
    }
  }

  async function handleCancelApply() {
    if (!studyId) return;
    await cancelApplication(studyId);
    setApplyStatus("NONE");
  }

  async function handleLeave() {
    if (!studyId) return;
    if (!confirm("정말 이 스터디에서 탈퇴할까요?")) return;
    setActionError(null);
    setActionPending(true);
    try {
      await leaveStudy(studyId);
      setApplyStatus("NONE");
      setStudy((prev) => (prev ? { ...prev, memberCount: prev.memberCount - 1 } : prev));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "탈퇴에 실패했습니다.");
    } finally {
      setActionPending(false);
    }
  }

  async function handleRegisterWaitlist() {
    if (!studyId) return;
    setActionError(null);
    setActionPending(true);
    try {
      await registerWaitlist(studyId);
      setWaitlistRegistered(true);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "빈자리 알림 등록에 실패했습니다.");
    } finally {
      setActionPending(false);
    }
  }

  async function handleCancelWaitlist() {
    if (!studyId) return;
    setActionError(null);
    setActionPending(true);
    try {
      await cancelWaitlist(studyId);
      setWaitlistRegistered(false);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "빈자리 알림 취소에 실패했습니다.");
    } finally {
      setActionPending(false);
    }
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
            {applyStatus === "NONE" && !isFull && (
              <button type="button" className="wide" onClick={handleApply} disabled={actionPending}>
                참여 신청하기
              </button>
            )}
            {applyStatus === "NONE" && isFull && !waitlistRegistered && (
              <button type="button" className="wide" onClick={handleRegisterWaitlist} disabled={actionPending}>
                빈자리 알림 등록
              </button>
            )}
            {applyStatus === "NONE" && isFull && waitlistRegistered && (
              <button type="button" className="wide secondary" onClick={handleCancelWaitlist} disabled={actionPending}>
                빈자리 알림 등록됨 (취소)
              </button>
            )}
            {applyStatus === "PENDING" && (
              <button type="button" className="wide secondary" onClick={handleCancelApply}>
                신청 취소
              </button>
            )}
            {applyStatus === "APPROVED" && (
              <button type="button" className="wide secondary" onClick={handleLeave} disabled={actionPending}>
                탈퇴하기
              </button>
            )}
            {applyStatus === "REJECTED" && <p className="host-hint">참여 신청이 거절됐어요.</p>}
          </>
        )}
        {!user && <p className="host-hint">참여 신청은 로그인 후 이용할 수 있습니다.</p>}
        {isOwner && (
          <div className="owner-actions">
            <Link to={`/studies/${study.id}/applications`} className="button">
              신청자 관리
            </Link>
            <Link to={`/studies/${study.id}/edit`} className="button secondary">
              스터디 수정
            </Link>
            <button type="button" className="danger" onClick={handleDelete}>
              스터디 삭제
            </button>
          </div>
        )}
        {actionError && <p className="error">{actionError}</p>}
      </div>
    </div>
  );
}
