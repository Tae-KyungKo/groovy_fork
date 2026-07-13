import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { createStudy, getStudy, updateStudy } from "../api/studies";
import { listTags } from "../api/tags";
import { TagPicker } from "../components/TagPicker";
import type { Tag } from "../types";

export function StudyFormPage() {
  const { studyId } = useParams();
  const isEdit = Boolean(studyId);
  const navigate = useNavigate();

  const [tags, setTags] = useState<Tag[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [capacity, setCapacity] = useState(4);
  const [tagIds, setTagIds] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(isEdit);

  useEffect(() => {
    listTags().then(setTags);
  }, []);

  useEffect(() => {
    if (!studyId) return;
    getStudy(studyId).then((study) => {
      setTitle(study.title);
      setDescription(study.description);
      setCapacity(study.capacity);
      setTagIds(study.tagIds);
      setLoading(false);
    });
  }, [studyId]);

  function toggleTag(tagId: string) {
    setTagIds((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    );
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const payload = { title, description, capacity, tagIds };
      const study = isEdit && studyId
        ? await updateStudy(studyId, payload)
        : await createStudy(payload);
      navigate(`/studies/${study.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "저장에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <p className="page-loading">불러오는 중...</p>;

  return (
    <div className="page">
      <form className="card form" onSubmit={handleSubmit}>
        <h1>{isEdit ? "스터디 수정" : "스터디 만들기"}</h1>
        <label>
          제목
          <input value={title} onChange={(e) => setTitle(e.target.value)} required />
        </label>
        <label>
          설명
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={4}
            required
          />
        </label>
        <label>
          정원
          <input
            type="number"
            min={1}
            value={capacity}
            onChange={(e) => setCapacity(Number(e.target.value))}
            required
          />
        </label>
        <label>
          태그
          <TagPicker tags={tags} selected={tagIds} onToggle={toggleTag} />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? "저장 중..." : isEdit ? "수정하기" : "만들기"}
        </button>
      </form>
    </div>
  );
}
