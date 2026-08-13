import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import remarkGfm from "remark-gfm";
import { Link, useNavigate, useParams } from "react-router-dom";
import { createMemoir, getMemoir, listMyStudyOptions, updateMemoir } from "../api/memoirs";
import { ChevronLeftIcon } from "../components/icons";
import type { MemoirStudyOption } from "../types";

type ContentMode = "write" | "preview";

export function MemoirFormPage() {
  const { memoirId } = useParams();
  const isEdit = Boolean(memoirId);
  const navigate = useNavigate();

  const [studyOptions, setStudyOptions] = useState<MemoirStudyOption[]>([]);
  const [studyId, setStudyId] = useState("");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [contentMode, setContentMode] = useState<ContentMode>("write");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(isEdit);

  const cancelTo = isEdit ? `/memoirs/${memoirId}` : "/memoirs";

  useEffect(() => {
    if (isEdit) return;
    listMyStudyOptions().then((options) => {
      setStudyOptions(options);
      if (options.length > 0) setStudyId(options[0].studyId);
    });
  }, [isEdit]);

  useEffect(() => {
    if (!memoirId) return;
    getMemoir(memoirId).then((memoir) => {
      setStudyId(memoir.studyId);
      setTitle(memoir.title);
      setContent(memoir.content);
      setLoading(false);
    });
  }, [memoirId]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const memoir =
        isEdit && memoirId
          ? await updateMemoir(memoirId, { title, content })
          : await createMemoir({ studyId, title, content });
      navigate(`/memoirs/${memoir.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "저장에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <p className="page-loading">불러오는 중...</p>;

  return (
    <div className="page detail-main">
      <Link to={cancelTo} className="back-link">
        <ChevronLeftIcon size={14} />
        {isEdit ? "회고록으로 돌아가기" : "홈으로"}
      </Link>
      <h1>{isEdit ? "회고록 수정" : "새 회고록 작성"}</h1>
      <form className="card form form-card" onSubmit={handleSubmit}>
        {!isEdit && (
          <label>
            <span>
              스터디 선택 <em className="req">*</em>
            </span>
            <select value={studyId} onChange={(e) => setStudyId(e.target.value)} required>
              {studyOptions.length === 0 && <option value="">참여 중인 스터디가 없습니다</option>}
              {studyOptions.map((option) => (
                <option key={option.studyId} value={option.studyId}>
                  {option.title}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          <span>
            제목 <em className="req">*</em>
          </span>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="회고록 제목을 입력해요"
            required
          />
        </label>
        <label>
          <div className="content-label-row">
            <span>
              내용 <em className="req">*</em>
            </span>
            <div className="content-mode-toggle">
              <button
                type="button"
                className={contentMode === "write" ? "small" : "small secondary"}
                onClick={() => setContentMode("write")}
              >
                작성
              </button>
              <button
                type="button"
                className={contentMode === "preview" ? "small" : "small secondary"}
                onClick={() => setContentMode("preview")}
              >
                미리보기
              </button>
            </div>
          </div>
          {contentMode === "write" ? (
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="마크다운 문법을 사용할 수 있어요"
              rows={12}
              required
            />
          ) : (
            <div className="markdown-body markdown-preview-box">
              {content ? (
                <Markdown remarkPlugins={[remarkGfm, remarkBreaks]}>{content}</Markdown>
              ) : (
                <p className="empty">미리볼 내용이 없습니다.</p>
              )}
            </div>
          )}
        </label>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="button" className="secondary" onClick={() => navigate(cancelTo)}>
            취소
          </button>
          <button type="submit" disabled={submitting || (!isEdit && studyOptions.length === 0)}>
            {submitting ? "저장 중..." : isEdit ? "수정하기" : "작성하기"}
          </button>
        </div>
      </form>
    </div>
  );
}
