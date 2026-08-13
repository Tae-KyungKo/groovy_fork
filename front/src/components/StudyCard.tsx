import { Link } from "react-router-dom";
import { LevelBadge } from "./LevelBadge";
import type { Study, Tag } from "../types";
import { DAY_LABELS } from "../types";
import { formatTime } from "../utils/date";

interface StudyCardProps {
  study: Study;
  tagsById: Map<number, Tag>;
  matchScore?: number;
}

const MAX_VISIBLE_TAGS = 3;

export function StudyCard({ study, tagsById, matchScore }: StudyCardProps) {
  const isFull = study.memberCount >= study.capacity;
  const visibleTagIds = study.tagIds.slice(0, MAX_VISIBLE_TAGS);
  const hiddenTagCount = study.tagIds.length - visibleTagIds.length;
  const scheduleParts = [
    study.meetingDays.length ? study.meetingDays.map((day) => DAY_LABELS[day]).join(", ") : null,
    study.meetingStartTime && study.meetingEndTime
      ? `${formatTime(study.meetingStartTime)} - ${formatTime(study.meetingEndTime)}`
      : null,
  ].filter(Boolean);

  return (
    <Link to={`/studies/${study.id}`} className="study-card">
      <div className="study-card-top">
        <span className={`study-status${isFull ? " full" : ""}`}>
          {isFull ? "모집마감" : "모집중"}
        </span>
        <LevelBadge level={study.level} />
      </div>
      <div className="tag-picker">
        {visibleTagIds.map((id) => (
          <span key={id} className="tag-chip">
            #{tagsById.get(id)?.name ?? id}
          </span>
        ))}
      </div>
      {hiddenTagCount > 0 && (
        <div className="tag-picker tag-picker-summary">
          <span className="tag-chip">..+{hiddenTagCount}</span>
        </div>
      )}
      <h3>{study.title}</h3>
      {matchScore !== undefined && (
        <span className="match-score">
          태그 매칭률 {Number.isInteger(matchScore) ? matchScore : matchScore.toFixed(2)}%
        </span>
      )}
      <p className="description">{study.description}</p>
      <div className="study-card-meta">
        <span>{scheduleParts.length ? scheduleParts.join(" · ") : "일정 미정"}</span>
        <span>
          {study.memberCount}/{study.capacity}명
        </span>
      </div>
    </Link>
  );
}
