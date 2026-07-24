import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { createStudy, getStudy, updateStudy } from "../api/studies";
import { listTags } from "../api/tags";
import { ChevronLeftIcon } from "../components/icons";
import { TagPicker } from "../components/TagPicker";
import type { DayOfWeek, Tag } from "../types";
import { DAY_LABELS, DAYS_OF_WEEK } from "../types";
import { formatTime } from "../utils/date";

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

  const cancelTo = isEdit ? `/studies/${studyId}` : "/studies";

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
      // time input은 "HH:mm"만 받으므로 백엔드의 "HH:mm:ss"에서 초를 잘라낸다.
      setMeetingStartTime(study.meetingStartTime ? formatTime(study.meetingStartTime) : "");
      setMeetingEndTime(study.meetingEndTime ? formatTime(study.meetingEndTime) : "");
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
        meetingStartTime: meetingStartTime || undefined,
        meetingEndTime: meetingEndTime || undefined,
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
    <div className="page detail-main">
      <Link to={cancelTo} className="back-link">
        <ChevronLeftIcon size={14} />
        {isEdit ? "스터디로 돌아가기" : "홈으로"}
      </Link>
      <h1>{isEdit ? "스터디 수정" : "새 스터디 만들기"}</h1>
      <form className="card form form-card" onSubmit={handleSubmit}>
        <label>
          <span>
            제목 <em className="req">*</em>
          </span>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="스터디 이름을 입력해요"
            required
          />
        </label>
        <label>
          <span>
            설명 <em className="req">*</em>
          </span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="어떤 스터디인지, 어떻게 진행되는지 소개해요"
            rows={4}
            required
          />
        </label>
        <div className="form-grid-2">
          <label>
            <span>
              정원 <em className="req">*</em>
            </span>
            <input
              type="number"
              min={1}
              value={capacity}
              onChange={(e) => setCapacity(Number(e.target.value))}
              required
            />
          </label>
          <div />
        </div>
        <label>
          <span>
            모임 요일 <em className="opt">(선택)</em>
          </span>
          <div className="tag-picker">
            {DAYS_OF_WEEK.map((day) => (
              <button
                key={day}
                type="button"
                className={`day-chip${meetingDays.includes(day) ? " active" : ""}`}
                onClick={() => toggleDay(day)}
              >
                {DAY_LABELS[day]}
              </button>
            ))}
          </div>
        </label>
        <div className="form-grid-2">
          <label>
            <span>
              시작 시각 <em className="opt">(선택)</em>
            </span>
            <input
              type="time"
              value={meetingStartTime}
              onChange={(e) => setMeetingStartTime(e.target.value)}
            />
          </label>
          <label>
            <span>
              종료 시각 <em className="opt">(선택)</em>
            </span>
            <input
              type="time"
              value={meetingEndTime}
              onChange={(e) => setMeetingEndTime(e.target.value)}
            />
          </label>
        </div>
        <label>
          <span>
            태그 <em className="opt">(선택)</em>
          </span>
          <TagPicker tags={tags} selected={tagIds} onToggle={toggleTag} />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="button" className="secondary" onClick={() => navigate(cancelTo)}>
            취소
          </button>
          <button type="submit" disabled={submitting}>
            {submitting ? "저장 중..." : isEdit ? "수정하기" : "스터디 만들기"}
          </button>
        </div>
      </form>
    </div>
  );
}
