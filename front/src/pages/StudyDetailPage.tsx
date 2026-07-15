import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { applyToStudy, cancelApplication, deleteStudy, getStudy } from "../api/studies";
import { listTags } from "../api/tags";
import { getWaitingPosition, joinWaiting, leaveWaiting } from "../api/waiting";
import { useAuth } from "../context/AuthContext";
import type { Study, Tag, WaitingPosition } from "../types";
import { DAY_LABELS } from "../types";

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

  return (
    <div className="page">
      <div className="card">
        <div className="page-header">
          <h1>{study.title}</h1>
          {isOwner && (
            <div className="button-row">
              <Link to={`/studies/${study.id}/edit`} className="button secondary">
                수정
              </Link>
              <Link to={`/studies/${study.id}/applications`} className="button secondary">
                신청 관리
              </Link>
              <button type="button" className="button danger" onClick={handleDelete}>
                삭제
              </button>
            </div>
          )}
        </div>
        <p className="description">{study.description}</p>
        <div className="tag-picker">
          {study.tagIds.map((id) => (
            <span key={id} className="tag-chip">
              #{tagsById.get(id)?.name ?? id}
            </span>
          ))}
        </div>
        <p className="capacity">
          정원 {study.memberCount} / {study.capacity}명 · 스터디장 {study.leaderName}
        </p>
        <p className="hint">
          {study.meetingDays.map((day) => DAY_LABELS[day]).join(", ")} {study.meetingStartTime} ~{" "}
          {study.meetingEndTime}
        </p>

        {!isOwner && user && (
          <div className="button-row">
            {applyState === "NONE" ? (
              <button type="button" onClick={handleApply} disabled={isFull}>
                {isFull ? "정원 마감" : "참여 신청"}
              </button>
            ) : (
              <button type="button" className="secondary" onClick={handleCancelApply}>
                신청 취소
              </button>
            )}

            {isFull &&
              (waiting ? (
                <button type="button" className="secondary" onClick={handleLeaveWaiting}>
                  대기 취소 (내 순번 {waiting.position}/{waiting.totalWaiting})
                </button>
              ) : (
                <button type="button" className="secondary" onClick={handleJoinWaiting}>
                  빈자리 대기 신청
                </button>
              ))}
          </div>
        )}
        {!user && <p className="hint">참여 신청은 로그인 후 이용할 수 있습니다.</p>}
        {actionError && <p className="error">{actionError}</p>}
      </div>
    </div>
  );
}
