import { Link } from "react-router-dom";
import type { Study, Tag } from "../types";
import { DAY_LABELS } from "../types";

interface StudyCardProps {
  study: Study;
  tagsById: Map<number, Tag>;
  matchScore?: number;
}

export function StudyCard({ study, tagsById, matchScore }: StudyCardProps) {
  const isFull = study.memberCount >= study.capacity;
  const scheduleParts = [
    study.meetingDays.length ? study.meetingDays.map((day) => DAY_LABELS[day]).join(", ") : null,
    study.meetingStartTime && study.meetingEndTime
      ? `${study.meetingStartTime} - ${study.meetingEndTime}`
      : null,
  ].filter(Boolean);

  return (
    <Link to={`/studies/${study.id}`} className="study-card">
      <span className={`study-status${isFull ? " full" : ""}`}>
        {isFull ? "모집마감" : "모집중"}
      </span>
      <div className="tag-picker">
        {study.tagIds.map((id) => (
          <span key={id} className="tag-chip">
            #{tagsById.get(id)?.name ?? id}
          </span>
        ))}
      </div>
      <h3>{study.title}</h3>
      {matchScore !== undefined && <span className="match-score">매칭 {matchScore}%</span>}
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
