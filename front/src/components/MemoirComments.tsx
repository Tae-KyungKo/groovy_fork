import { useState } from "react";
import type { FormEvent } from "react";
import { createComment, deleteComment, updateComment } from "../api/memoirs";
import { useAuth } from "../context/AuthContext";
import type { MemoirComment } from "../types";

interface MemoirCommentsProps {
  memoirId: string;
  comments: MemoirComment[];
  onChange: (comments: MemoirComment[]) => void;
}

export function MemoirComments({ memoirId, comments, onChange }: MemoirCommentsProps) {
  const { user } = useAuth();
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState("");

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!content.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const created = await createComment(memoirId, content);
      onChange([...comments, created]);
      setContent("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "댓글 작성에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  function startEdit(comment: MemoirComment) {
    setEditingId(comment.id);
    setEditingContent(comment.content);
  }

  async function handleUpdate(commentId: string) {
    if (!editingContent.trim()) return;
    const updated = await updateComment(memoirId, commentId, editingContent);
    onChange(comments.map((c) => (c.id === commentId ? updated : c)));
    setEditingId(null);
  }

  async function handleDelete(commentId: string) {
    if (!confirm("댓글을 삭제할까요?")) return;
    await deleteComment(memoirId, commentId);
    onChange(comments.filter((c) => c.id !== commentId));
  }

  return (
    <section className="section-block">
      <h2 className="section-title">댓글 {comments.length}개</h2>
      <ul className="list">
        {comments.map((comment) => (
          <li key={comment.id} className="card list-item comment-item">
            {editingId === comment.id ? (
              <div className="comment-edit">
                <textarea
                  value={editingContent}
                  onChange={(e) => setEditingContent(e.target.value)}
                  rows={2}
                />
                <div className="button-row">
                  <button type="button" onClick={() => handleUpdate(comment.id)}>
                    저장
                  </button>
                  <button type="button" className="secondary" onClick={() => setEditingId(null)}>
                    취소
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div>
                  <p className="strong">{comment.authorName}</p>
                  <p className="comment-content">{comment.content}</p>
                  <time>{new Date(comment.createdAt).toLocaleString()}</time>
                </div>
                {user && user.id === comment.authorId && (
                  <div className="button-row">
                    <button type="button" className="secondary" onClick={() => startEdit(comment)}>
                      수정
                    </button>
                    <button type="button" className="danger" onClick={() => handleDelete(comment.id)}>
                      삭제
                    </button>
                  </div>
                )}
              </>
            )}
          </li>
        ))}
        {comments.length === 0 && <p className="empty">댓글이 없습니다.</p>}
      </ul>

      {user ? (
        <form className="form comment-form" onSubmit={handleSubmit}>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="댓글을 입력하세요"
            rows={2}
            required
          />
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? "작성 중..." : "댓글 작성"}
          </button>
        </form>
      ) : (
        <p className="hint">댓글 작성은 로그인 후 이용할 수 있습니다.</p>
      )}
    </section>
  );
}
