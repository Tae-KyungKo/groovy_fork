import { useEffect, useState } from "react";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import remarkGfm from "remark-gfm";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteMemoir, getMemoir, likeMemoir, listComments, unlikeMemoir } from "../api/memoirs";
import { ChevronLeftIcon, HeartIcon } from "../components/icons";
import { LevelBadge } from "../components/LevelBadge";
import { MemoirComments } from "../components/MemoirComments";
import { useAuth } from "../context/AuthContext";
import type { Memoir, MemoirComment } from "../types";

export function MemoirDetailPage() {
  const { memoirId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [memoir, setMemoir] = useState<Memoir | null>(null);
  const [comments, setComments] = useState<MemoirComment[]>([]);
  const [loading, setLoading] = useState(true);

  const isAuthor = user && memoir && user.id === memoir.authorId;

  useEffect(() => {
    if (!memoirId) return;
    setLoading(true);
    Promise.all([getMemoir(memoirId), listComments(memoirId)]).then(([memoirData, commentList]) => {
      setMemoir(memoirData);
      setComments(commentList);
      setLoading(false);
    });
  }, [memoirId]);

  async function handleDelete() {
    if (!memoirId) return;
    if (!confirm("정말 삭제할까요?")) return;
    await deleteMemoir(memoirId);
    navigate("/memoirs");
  }

  async function handleToggleLike() {
    if (!user || !memoir) return;
    const updated = memoir.liked ? await unlikeMemoir(memoir.id) : await likeMemoir(memoir.id);
    setMemoir(updated);
  }

  if (loading) return <p className="page-loading">불러오는 중...</p>;
  if (!memoir) return <p className="empty">회고록을 찾을 수 없습니다.</p>;

  return (
    <div className="page detail-main">
      <Link to="/memoirs" className="back-link">
        <ChevronLeftIcon size={14} />
        회고록 목록
      </Link>

      <div>
        <div className="tag-picker detail-tags">
          <span className="tag-chip">{memoir.studyTitle}</span>
          <LevelBadge level={memoir.studyLevel} expPoint={memoir.studyExpPoint} />
        </div>
        <h1 className="detail-title">제목 : {memoir.title}</h1>
        <div className="host-row">
          <span className="avatar avatar-sm">{memoir.authorName.charAt(0)}</span>
          <span className="host-name">{memoir.authorName}</span>
          <button
            type="button"
            className={`like-button${memoir.liked ? " liked" : ""}`}
            disabled={!user}
            onClick={handleToggleLike}
          >
            <HeartIcon size={16} filled={memoir.liked} />
            {memoir.likeCount}
          </button>
        </div>
      </div>

      <section className="section-block markdown-body">
        <Markdown remarkPlugins={[remarkGfm, remarkBreaks]}>{memoir.content}</Markdown>
      </section>

      {isAuthor && (
        <div className="action-bar">
          <div className="owner-actions">
            <Link to={`/memoirs/${memoir.id}/edit`} className="button secondary">
              회고록 수정
            </Link>
            <button type="button" className="danger" onClick={handleDelete}>
              회고록 삭제
            </button>
          </div>
        </div>
      )}

      <MemoirComments memoirId={memoir.id} comments={comments} onChange={setComments} />
    </div>
  );
}
