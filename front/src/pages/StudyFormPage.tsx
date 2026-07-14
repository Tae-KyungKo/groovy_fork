import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { createStudy, getStudy, updateStudy } from "../api/studies";
import { listTags } from "../api/tags";
import { TagPicker } from "../components/TagPicker";
import type { DayOfWeek, Tag } from "../types";
import { DAY_LABELS, DAYS_OF_WEEK } from "../types";

export function StudyFormPage() {
  const { studyId } = useParams();
  const isEdit = Boolean(studyId);
  const navigate = useNavigate();

  const [tags, setTags] = useState<Tag[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [capacity, setCapacity] = useState(4);
  const [tagIds, setTagIds] = useState<number[]>([]);
  const [meetingDays, setMeetingDays] = useState<DayOfWeek[]>([]);
  const [meetingStartTime, setMeetingStartTime] = useState("");
  const [meetingEndTime, setMeetingEndTime] = useState("");
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
      setMeetingDays(study.meetingDays);
      setMeetingStartTime(study.meetingStartTime);
      setMeetingEndTime(study.meetingEndTime);
      setLoading(false);
    });
  }, [studyId]);

  function toggleTag(tagId: number) {
    setTagIds((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    );
  }

  function toggleDay(day: DayOfWeek) {
    setMeetingDays((prev) =>
      prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day],
    );
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const payload = {
        title,
        description,
        capacity,
        tagIds,
        meetingDays,
        meetingStartTime,
        meetingEndTime,
      };
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
          모임 요일
          <div className="tag-picker">
            {DAYS_OF_WEEK.map((day) => (
              <button
                key={day}
                type="button"
                className={`tag-chip${meetingDays.includes(day) ? " active" : ""}`}
                onClick={() => toggleDay(day)}
              >
                {DAY_LABELS[day]}
              </button>
            ))}
          </div>
        </label>
        <label>
          시작 시각
          <input
            type="time"
            value={meetingStartTime}
            onChange={(e) => setMeetingStartTime(e.target.value)}
            required
          />
        </label>
        <label>
          종료 시각
          <input
            type="time"
            value={meetingEndTime}
            onChange={(e) => setMeetingEndTime(e.target.value)}
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
