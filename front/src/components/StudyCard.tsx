import { Link } from "react-router-dom";
import type { Study, Tag } from "../types";

interface StudyCardProps {
  study: Study;
  tagsById: Map<string, Tag>;
  matchScore?: number;
}

export function StudyCard({ study, tagsById, matchScore }: StudyCardProps) {
  return (
    <Link to={`/studies/${study.id}`} className="study-card">
      <div className="study-card-top">
        <h3>{study.title}</h3>
        {matchScore !== undefined && <span className="match-score">매칭 {matchScore}%</span>}
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
        {study.memberCount} / {study.capacity}명
      </p>
    </Link>
  );
}
