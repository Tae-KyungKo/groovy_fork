import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { listStudies } from "../api/studies";
import { listMyTagIds, listTags, matchStudies, saveMyTags } from "../api/tags";
import { StudyCard } from "../components/StudyCard";
import { TagPicker } from "../components/TagPicker";
import { useAuth } from "../context/AuthContext";
import type { Study, StudyMatch, Tag } from "../types";

export function StudyListPage() {
  const { user } = useAuth();
  const [studies, setStudies] = useState<Study[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [tags, setTags] = useState<Tag[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);
  const [matches, setMatches] = useState<StudyMatch[] | null>(null);
  // 매칭 조회에 실제로 쓰인 태그(저장된 선호 태그 모드면 undefined). 매칭 페이지 이동 시 동일 조건으로 다시 조회하기 위해 기억해 둔다.
  const [matchQueryTagIds, setMatchQueryTagIds] = useState<number[] | undefined>(undefined);
  const [matchPage, setMatchPage] = useState(0);
  const [matchTotalPages, setMatchTotalPages] = useState(1);
  const [matching, setMatching] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listTags().then(setTags);
  }, []);

  useEffect(() => {
    if (!user) return;
    listMyTagIds().then(setSelectedTagIds);
  }, [user]);

  useEffect(() => {
    setLoading(true);
    listStudies(page).then((result) => {
      setStudies(result.studies);
      setTotalPages(result.totalPages);
      setLoading(false);
    });
  }, [page]);

  const tagsById = useMemo(() => new Map(tags.map((t) => [t.id, t])), [tags]);

  function toggleTag(tagId: number) {
    setSelectedTagIds((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    );
  }

  async function loadMatches(tagIdsForQuery: number[] | undefined, page: number) {
    setMatching(true);
    try {
      const result = await matchStudies(tagIdsForQuery, page);
      setMatches(result.matches);
      setMatchQueryTagIds(tagIdsForQuery);
      setMatchPage(result.page);
      setMatchTotalPages(result.totalPages);
    } finally {
      setMatching(false);
    }
  }

  async function handleShowMatches() {
    setMatching(true);
    try {
      await saveMyTags(selectedTagIds);
    } finally {
      setMatching(false);
    }
    await loadMatches(undefined, 0);
  }

  async function handlePreviewMatches() {
    await loadMatches(selectedTagIds, 0);
  }

  function clearMatches() {
    setMatches(null);
    setSelectedTagIds([]);
    setMatchQueryTagIds(undefined);
    setMatchPage(0);
    setMatchTotalPages(1);
  }

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

      {user && (
        <div className="card">
          <p className="hint">선호 태그를 선택하고 저장하면 매칭도 순으로 볼 수 있습니다.</p>
          <TagPicker tags={tags} selected={selectedTagIds} onToggle={toggleTag} />
          <div className="button-row tag-actions">
            <button type="button" onClick={handleShowMatches} disabled={matching || selectedTagIds.length === 0}>
              {matching ? "매칭 중..." : "태그 저장"}
            </button>
            <button
              type="button"
              className="secondary"
              onClick={handlePreviewMatches}
              disabled={matching || selectedTagIds.length === 0}
            >
              {matching ? "매칭 중..." : "태그 적용"}
            </button>
            {matches && (
              <button type="button" className="secondary" onClick={clearMatches}>
                매칭 해제
              </button>
            )}
          </div>
        </div>
      )}

      {loading ? (
        <p className="page-loading">불러오는 중...</p>
      ) : matches ? (
        <>
          <div className="study-grid">
            {matches.map((match) => (
              <StudyCard
                key={match.study.id}
                study={match.study}
                tagsById={tagsById}
                matchScore={match.matchScore}
              />
            ))}
            {matches.length === 0 && <p className="empty">매칭된 스터디가 없습니다.</p>}
          </div>
          {matchTotalPages > 1 && (
            <div className="button-row">
              {Array.from({ length: matchTotalPages }, (_, i) => i).map((pageIndex) => (
                <button
                  key={pageIndex}
                  type="button"
                  className={pageIndex === matchPage ? undefined : "secondary"}
                  disabled={matching}
                  onClick={() => loadMatches(matchQueryTagIds, pageIndex)}
                >
                  {pageIndex + 1}
                </button>
              ))}
            </div>
          )}
        </>
      ) : (
        <>
          <div className="study-grid">
            {studies.map((study) => (
              <StudyCard key={study.id} study={study} tagsById={tagsById} />
            ))}
            {studies.length === 0 && <p className="empty">스터디가 없습니다.</p>}
          </div>
          {totalPages > 1 && (
            <div className="button-row">
              {Array.from({ length: totalPages }, (_, i) => i).map((pageIndex) => (
                <button
                  key={pageIndex}
                  type="button"
                  className={pageIndex === page ? undefined : "secondary"}
                  onClick={() => setPage(pageIndex)}
                >
                  {pageIndex + 1}
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
