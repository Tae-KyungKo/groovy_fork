import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { likeMemoir, listMemoirs, listMyMemoirs, unlikeMemoir } from "../api/memoirs";
import { CommentIcon, HeartIcon, SearchIcon } from "../components/icons";
import { LevelBadge } from "../components/LevelBadge";
import { useAuth } from "../context/AuthContext";
import type { Memoir, MemoirSort } from "../types";
import { stripMarkdown, truncate } from "../utils/markdown";

type Tab = "home" | "mine";

const PREVIEW_LENGTH = 90;

export function MemoirListPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState<Tab>("home");
  const [memoirs, setMemoirs] = useState<Memoir[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [sortBy, setSortBy] = useState<MemoirSort>("latest");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    const request = tab === "mine" ? listMyMemoirs(page) : listMemoirs({ page, keyword, sortBy });
    request.then((result) => {
      setMemoirs(result.memoirs);
      setTotalPages(result.totalPages);
      setLoading(false);
    });
  }, [tab, page, keyword, sortBy]);

  function handleSearchSubmit(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setKeyword(searchInput.trim());
  }

  function handleTabChange(next: Tab) {
    setTab(next);
    setPage(0);
  }

  function handleSortChange(next: MemoirSort) {
    setSortBy(next);
    setPage(0);
  }

  async function handleToggleLike(memoir: Memoir) {
    if (!user) return;
    const updated = memoir.liked ? await unlikeMemoir(memoir.id) : await likeMemoir(memoir.id);
    setMemoirs((prev) => prev.map((m) => (m.id === memoir.id ? updated : m)));
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>회고록</h1>
        {user && (
          <Link to="/memoirs/new" className="button">
            회고록 작성
          </Link>
        )}
      </div>

      {user && (
        <div className="button-row memoir-tabs">
          <button type="button" className={tab === "home" ? undefined : "secondary"} onClick={() => handleTabChange("home")}>
            홈
          </button>
          <button type="button" className={tab === "mine" ? undefined : "secondary"} onClick={() => handleTabChange("mine")}>
            나의 활동
          </button>
        </div>
      )}

      {tab === "home" && (
        <div className="memoir-toolbar">
          <form className="memoir-search" onSubmit={handleSearchSubmit}>
            <SearchIcon size={16} />
            <input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="키워드나 작성자로 검색"
            />
          </form>
          <div className="button-row memoir-sort">
            <button
              type="button"
              className={sortBy === "latest" ? "small" : "small secondary"}
              onClick={() => handleSortChange("latest")}
            >
              최신
            </button>
            <button
              type="button"
              className={sortBy === "popular" ? "small" : "small secondary"}
              onClick={() => handleSortChange("popular")}
            >
              인기
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <p className="page-loading">불러오는 중...</p>
      ) : (
        <>
          <div className="memoir-grid">
            {memoirs.map((memoir) => (
              <article key={memoir.id} className="memoir-card">
                <Link to={`/memoirs/${memoir.id}`} className="memoir-card-link">
                  <div className="memoir-card-top">
                    <span className="tag-chip">{memoir.studyTitle}</span>
                    <LevelBadge level={memoir.studyLevel} />
                  </div>
                  <h3>{memoir.title}</h3>
                  <p className="memoir-preview">{truncate(stripMarkdown(memoir.content), PREVIEW_LENGTH)}</p>
                </Link>
                <div className="memoir-card-footer">
                  <button
                    type="button"
                    className={`like-button${memoir.liked ? " liked" : ""}`}
                    disabled={!user}
                    onClick={() => handleToggleLike(memoir)}
                  >
                    <HeartIcon size={15} filled={memoir.liked} />
                    {memoir.likeCount}
                  </button>
                  <span className="memoir-card-stat">
                    <CommentIcon size={15} />
                    {memoir.commentCount}
                  </span>
                  <span className="memoir-card-author">{memoir.authorName}</span>
                </div>
              </article>
            ))}
            {memoirs.length === 0 && (
              <p className="empty">{tab === "mine" ? "작성한 회고록이 없습니다." : "회고록이 없습니다."}</p>
            )}
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
