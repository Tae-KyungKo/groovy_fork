import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { listStudies } from "../api/studies";
import { listTags, matchStudies } from "../api/tags";
import { StudyCard } from "../components/StudyCard";
import { TagPicker } from "../components/TagPicker";
import { useAuth } from "../context/AuthContext";
import type { Study, StudyMatch, Tag } from "../types";

export function StudyListPage() {
  const { user } = useAuth();
  const [studies, setStudies] = useState<Study[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>([]);
  const [matches, setMatches] = useState<StudyMatch[] | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([listStudies(), listTags()]).then(([studyList, tagList]) => {
      setStudies(studyList);
      setTags(tagList);
      setLoading(false);
    });
  }, []);

  useEffect(() => {
    if (selectedTagIds.length === 0) {
      setMatches(null);
      return;
    }
    matchStudies(selectedTagIds).then(setMatches);
  }, [selectedTagIds]);

  const tagsById = useMemo(() => new Map(tags.map((t) => [t.id, t])), [tags]);

  function toggleTag(tagId: string) {
    setSelectedTagIds((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    );
  }

  const displayList = matches ?? studies;

  return (
    <div className="page">
      <div className="page-header">
        <h1>스터디 목록</h1>
        {user && (
          <Link to="/studies/new" className="button">
            스터디 만들기
          </Link>
        )}
      </div>

      <div className="card">
        <p className="hint">태그를 선택하면 매칭도 순으로 정렬됩니다.</p>
        <TagPicker tags={tags} selected={selectedTagIds} onToggle={toggleTag} />
      </div>

      {loading ? (
        <p className="page-loading">불러오는 중...</p>
      ) : (
        <div className="study-grid">
          {displayList.map((study) => (
            <StudyCard
              key={study.id}
              study={study}
              tagsById={tagsById}
              matchScore={"matchScore" in study ? (study as StudyMatch).matchScore : undefined}
            />
          ))}
          {displayList.length === 0 && <p className="empty">스터디가 없습니다.</p>}
        </div>
      )}
    </div>
  );
}
