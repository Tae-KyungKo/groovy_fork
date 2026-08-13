import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import {
  addCalendarEvent,
  deleteCalendarEvent,
  listCalendarEvents,
  listMyStudyOptions,
  updateCalendarEvent,
} from "../api/calendars";
import { Modal } from "../components/Modal";
import { ChevronLeftIcon, ChevronRightIcon } from "../components/icons";
import type { CalendarEvent, CalendarStudyOption } from "../types";
import { addMonths, buildMonthGrid, startOfMonth, toDateKey } from "../utils/date";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const MAX_VISIBLE_LANES = 3;

type ScheduleType = "PERSONAL" | "STUDY";

// 한 칸(day cell)에 놓일 이벤트 한 줄. null은 "이 칸에는 없지만 같은 주의 다른 날에는
// 이어지는 일정이 있는 레인"이라 자리만 비워서, 여러 날짜에 걸친 막대가 옆 칸과 수직으로
// 어긋나지 않게 맞추기 위한 자리 표시자다.
type LaneSlot = CalendarEvent | null;

interface RowLayout {
  laneByDate: Map<string, LaneSlot[]>;
  hiddenCountByDate: Map<string, number>;
}

// 날짜 셀 클릭 시 뜨는 모달들. day-choice/day-list는 "그 날짜에 이미 일정이 있을 때"만 거치고,
// 일정이 없는 날짜를 클릭하면 바로 create로 진입한다(기존 UX 유지).
type PageModal =
  | { kind: "day-choice"; dateKey: string; dayEvents: CalendarEvent[] }
  | { kind: "day-list"; dateKey: string; dayEvents: CalendarEvent[] }
  | { kind: "create"; dateKey: string }
  | { kind: "detail"; event: CalendarEvent }
  | { kind: "edit"; event: CalendarEvent }
  | null;

function formatMonthDay(dateKey: string) {
  return dateKey.slice(5).replace("-", ".");
}

function eventsOnDate(events: CalendarEvent[], dateKey: string): CalendarEvent[] {
  return events.filter((ev) => ev.startDate <= dateKey && dateKey <= ev.endDate);
}

// "오늘 일정" 패널과 "일정 보기" 모달에서 공통으로 쓰는 일정 요약 행. 클릭하면 상세 조회로 진입한다.
function EventListItem({ event, onSelect }: { event: CalendarEvent; onSelect: (event: CalendarEvent) => void }) {
  const isStudy = event.type === "STUDY";
  return (
    <li
      className="today-item"
      role="button"
      tabIndex={0}
      style={{ cursor: "pointer" }}
      onClick={() => onSelect(event)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSelect(event);
        }
      }}
    >
      <span className={`today-dot ${isStudy ? "study" : "personal"}`} />
      <div className="today-item-body">
        <p className="today-item-title">{event.title}</p>
        <p className="today-item-sub">
          {isStudy ? event.studyTitle : `${formatMonthDay(event.startDate)} ~ ${formatMonthDay(event.endDate)}`}
        </p>
      </div>
      <span className={`today-badge ${isStudy ? "study" : "personal"}`}>{isStudy ? "스터디" : "개인"}</span>
    </li>
  );
}

// 같은 주(일~토) 안에서 겹치는 일정들에게 겹치지 않는 "레인(줄)" 번호를 배정한다.
// 여러 날짜에 걸친 일정은 그 주 안에서 항상 같은 레인을 쓰게 되므로, 옆 칸으로 넘어가도
// 막대가 같은 높이에서 이어져 보인다(겹칠 때 줄이 어긋나던 문제 해결).
function layoutWeekRow(rowDays: Date[], events: CalendarEvent[]): RowLayout {
  const rowStartKey = toDateKey(rowDays[0]);
  const rowEndKey = toDateKey(rowDays[rowDays.length - 1]);

  const segments = events
    .filter((event) => event.startDate <= rowEndKey && event.endDate >= rowStartKey)
    .map((event) => ({
      event,
      segStart: event.startDate > rowStartKey ? event.startDate : rowStartKey,
      segEnd: event.endDate < rowEndKey ? event.endDate : rowEndKey,
    }))
    // 먼저 시작하는 일정부터, 시작일이 같으면 더 긴 일정부터 배치해 레인이 덜 쪼개지게 한다.
    .sort((a, b) => a.segStart.localeCompare(b.segStart) || b.segEnd.localeCompare(a.segEnd));

  const laneEnds: string[] = [];
  const laneByEventId = new Map<string, number>();
  for (const seg of segments) {
    let lane = laneEnds.findIndex((end) => end < seg.segStart);
    if (lane === -1) {
      lane = laneEnds.length;
      laneEnds.push(seg.segEnd);
    } else {
      laneEnds[lane] = seg.segEnd;
    }
    laneByEventId.set(seg.event.id, lane);
  }

  const visibleLaneCount = Math.min(laneEnds.length, MAX_VISIBLE_LANES);
  const laneByDate = new Map<string, LaneSlot[]>();
  const hiddenCountByDate = new Map<string, number>();

  for (const day of rowDays) {
    const dateKey = toDateKey(day);
    const lanes: LaneSlot[] = new Array(visibleLaneCount).fill(null);
    let hidden = 0;

    for (const seg of segments) {
      if (dateKey < seg.segStart || dateKey > seg.segEnd) continue;
      const lane = laneByEventId.get(seg.event.id)!;
      if (lane < MAX_VISIBLE_LANES) {
        lanes[lane] = seg.event;
      } else {
        hidden += 1;
      }
    }

    laneByDate.set(dateKey, lanes);
    hiddenCountByDate.set(dateKey, hidden);
  }

  return { laneByDate, hiddenCountByDate };
}

export function CalendarPage() {
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [studyOptions, setStudyOptions] = useState<CalendarStudyOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()));
  const [modal, setModal] = useState<PageModal>(null);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  // edit 모드에서만 사용자가 바꿀 수 있다. create 모드의 시작일은 클릭한 날짜(modal.dateKey)로 고정된다.
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [scheduleType, setScheduleType] = useState<ScheduleType>("PERSONAL");
  const [studyId, setStudyId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  useEffect(() => {
    refresh();
    listMyStudyOptions().then(setStudyOptions);
  }, []);

  function refresh() {
    setLoading(true);
    listCalendarEvents().then((list) => {
      setEvents(list);
      setLoading(false);
    });
  }

  const gridDays = useMemo(() => buildMonthGrid(currentMonth), [currentMonth]);

  const { laneByDate, hiddenCountByDate } = useMemo(() => {
    const laneByDate = new Map<string, LaneSlot[]>();
    const hiddenCountByDate = new Map<string, number>();
    for (let i = 0; i < gridDays.length; i += 7) {
      const row = layoutWeekRow(gridDays.slice(i, i + 7), events);
      row.laneByDate.forEach((value, key) => laneByDate.set(key, value));
      row.hiddenCountByDate.forEach((value, key) => hiddenCountByDate.set(key, value));
    }
    return { laneByDate, hiddenCountByDate };
  }, [events, gridDays]);
  const todayKey = toDateKey(new Date());
  const today = new Date();
  const todayLabel = `${today.getFullYear()}년 ${today.getMonth() + 1}월 ${today.getDate()}일 ${
    WEEKDAYS[today.getDay()]
  }요일`;
  const todayEvents = useMemo(() => eventsOnDate(events, todayKey), [events, todayKey]);

  function closeModal() {
    setModal(null);
    setTitle("");
    setContent("");
    setStartDate("");
    setEndDate("");
    setScheduleType("PERSONAL");
    setStudyId("");
    setModalError(null);
  }

  function openCreateModal(dateKey: string) {
    setTitle("");
    setContent("");
    setEndDate(dateKey);
    setScheduleType("PERSONAL");
    setStudyId("");
    setModalError(null);
    setModal({ kind: "create", dateKey });
  }

  // 날짜 셀 클릭: 그 날짜에 이미 일정이 있으면 "일정 보기 / 일정 생성" 선택 모달을,
  // 없으면 바로 생성 폼을 연다.
  function openDayCell(dateKey: string) {
    const dayEvents = eventsOnDate(events, dateKey);
    if (dayEvents.length === 0) {
      openCreateModal(dateKey);
      return;
    }
    setModalError(null);
    setModal({ kind: "day-choice", dateKey, dayEvents });
  }

  function openDetail(event: CalendarEvent) {
    setModalError(null);
    setModal({ kind: "detail", event });
  }

  function openEdit(event: CalendarEvent) {
    setTitle(event.title);
    setContent(event.content ?? "");
    setStartDate(event.startDate);
    setEndDate(event.endDate);
    setModalError(null);
    setModal({ kind: "edit", event });
  }

  async function handleCreateSubmit(formEvent: FormEvent) {
    formEvent.preventDefault();
    if (modal?.kind !== "create") return;
    const dateKey = modal.dateKey;
    if (!title) return;
    if (scheduleType === "STUDY" && !studyId) return;
    if (endDate && endDate < dateKey) return;
    setSubmitting(true);
    setModalError(null);
    try {
      await addCalendarEvent({
        title,
        content: content || undefined,
        startDate: dateKey,
        endDate: endDate || dateKey,
        studyId: scheduleType === "STUDY" ? studyId : undefined,
      });
      refresh();
      closeModal();
    } catch (err) {
      setModalError(err instanceof Error ? err.message : "일정 추가에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleUpdateSubmit(formEvent: FormEvent) {
    formEvent.preventDefault();
    if (modal?.kind !== "edit") return;
    if (!title || !startDate) return;
    if (endDate && endDate < startDate) return;
    setSubmitting(true);
    setModalError(null);
    try {
      await updateCalendarEvent(modal.event.calendarId, {
        title,
        content: content || undefined,
        startDate,
        endDate: endDate || startDate,
      });
      refresh();
      closeModal();
    } catch (err) {
      setModalError(err instanceof Error ? err.message : "일정 수정에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(event: CalendarEvent) {
    if (!window.confirm("이 일정을 삭제할까요?")) return;
    setSubmitting(true);
    setModalError(null);
    try {
      await deleteCalendarEvent(event.calendarId);
      refresh();
      closeModal();
    } catch (err) {
      setModalError(err instanceof Error ? err.message : "일정 삭제에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>캘린더</h1>
        <div className="button-row">
          <button
            type="button"
            className="icon-button"
            aria-label="이전 달"
            onClick={() => setCurrentMonth((m) => addMonths(m, -1))}
          >
            <ChevronLeftIcon size={16} />
          </button>
          <span className="strong month-label">
            {currentMonth.getFullYear()}년 {currentMonth.getMonth() + 1}월
          </span>
          <button
            type="button"
            className="icon-button"
            aria-label="다음 달"
            onClick={() => setCurrentMonth((m) => addMonths(m, 1))}
          >
            <ChevronRightIcon size={16} />
          </button>
          <button type="button" className="secondary" onClick={() => setCurrentMonth(startOfMonth(new Date()))}>
            오늘
          </button>
        </div>
      </div>

      {loading ? (
        <p className="page-loading">불러오는 중...</p>
      ) : (
        <div className="calendar-layout">
        <div className="calendar-scroll">
          <div className="calendar-grid">
            {WEEKDAYS.map((day) => (
              <div key={day} className="calendar-weekday">
                {day}
              </div>
            ))}
            {gridDays.map((date) => {
              const dateKey = toDateKey(date);
              const lanes = laneByDate.get(dateKey) ?? [];
              const hiddenCount = hiddenCountByDate.get(dateKey) ?? 0;
              const totalCount = lanes.filter(Boolean).length + hiddenCount;
              const isOtherMonth = date.getMonth() !== currentMonth.getMonth();
              const isToday = dateKey === todayKey;

              return (
                <button
                  key={dateKey}
                  type="button"
                  className={`calendar-cell${isOtherMonth ? " other-month" : ""}${isToday ? " today" : ""}`}
                  onClick={() => openDayCell(dateKey)}
                  aria-label={`${dateKey} 일정${totalCount ? `, 일정 ${totalCount}건` : " 추가"}`}
                >
                  <span className="calendar-cell-date">{date.getDate()}</span>
                  <span className="calendar-cell-events">
                    {lanes.map((event, lane) => {
                      if (!event) {
                        // 같은 주의 다른 날에 걸쳐 있는 일정이 이 칸에는 없는 레인.
                        // 자리를 비워 둬야 옆 칸의 막대와 줄이 어긋나지 않는다.
                        return <span key={`empty-${lane}`} className="calendar-event calendar-event-placeholder" />;
                      }

                      const label = event.type === "STUDY" ? event.studyTitle ?? event.title : event.title;
                      const isRange = event.startDate !== event.endDate;
                      const isEventStart = dateKey === event.startDate;
                      const isEventEnd = dateKey === event.endDate;
                      // 달력이 일요일부터 시작하는 주 단위 행으로 줄바꿈되므로, 여러 날짜에 걸친
                      // 일정은 각 행(일~토)마다 별도의 막대로 끊어 그려야 하고, 그 행 안에서
                      // 실제 시작/종료가 아니라 요일 경계 때문에 끊긴 지점은 둥글게 처리하지 않는다.
                      const isWeekRowStart = date.getDay() === 0;
                      const isWeekRowEnd = date.getDay() === 6;
                      const segStart = isEventStart || isWeekRowStart;
                      const segEnd = isEventEnd || isWeekRowEnd;
                      // 제목 반복으로 인한 가독성 저하를 막기 위해, 각 행에서 막대가 시작되는
                      // 칸에서만 제목을 보여주고 중간 구간은 색상 막대만으로 이어짐을 표현한다.
                      const showLabel = !isRange || segStart;

                      return (
                        <span
                          key={event.id}
                          className={[
                            "calendar-event",
                            `calendar-event-${event.type.toLowerCase()}`,
                            isRange && "calendar-event-range",
                            isRange && segStart && "seg-start",
                            isRange && segEnd && "seg-end",
                          ]
                            .filter(Boolean)
                            .join(" ")}
                          title={isRange ? `${label} (${event.startDate} ~ ${event.endDate})` : label}
                        >
                          {showLabel ? label : " "}
                        </span>
                      );
                    })}
                    {hiddenCount > 0 && <span className="calendar-event-more">+{hiddenCount}개</span>}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        <aside className="card calendar-side">
          <h2>오늘 일정</h2>
          <p className="today-date">{todayLabel}</p>
          {todayEvents.length === 0 ? (
            <div className="side-empty">
              <p className="side-empty-title">오늘은 일정이 없어요</p>
              <p className="side-empty-desc">달력에서 날짜를 클릭해 일정을 추가해보세요</p>
            </div>
          ) : (
            <ul className="today-list">
              {todayEvents.map((ev) => (
                <EventListItem key={ev.id} event={ev} onSelect={openDetail} />
              ))}
            </ul>
          )}
        </aside>
        </div>
      )}

      {modal?.kind === "day-choice" && (
        <Modal title={`${formatMonthDay(modal.dateKey)} 일정`} onClose={closeModal}>
          <div className="form">
            <p className="hint">이 날짜에 일정이 {modal.dayEvents.length}건 있어요. 무엇을 할까요?</p>
            <div className="form-actions">
              <button
                type="button"
                className="secondary"
                onClick={() => setModal({ kind: "day-list", dateKey: modal.dateKey, dayEvents: modal.dayEvents })}
              >
                일정 보기
              </button>
              <button type="button" onClick={() => openCreateModal(modal.dateKey)}>
                일정 생성
              </button>
            </div>
          </div>
        </Modal>
      )}

      {modal?.kind === "day-list" && (
        <Modal title={`${formatMonthDay(modal.dateKey)} 일정 목록`} onClose={closeModal}>
          <div className="form">
            <ul className="today-list">
              {modal.dayEvents.map((ev) => (
                <EventListItem key={ev.id} event={ev} onSelect={openDetail} />
              ))}
            </ul>
            <div className="form-actions">
              <button type="button" className="secondary" onClick={() => openCreateModal(modal.dateKey)}>
                새 일정 생성
              </button>
            </div>
          </div>
        </Modal>
      )}

      {modal?.kind === "detail" && (
        <Modal title="일정 상세" onClose={closeModal} className="modal-lg">
          <div className="event-detail">
            <div className="event-detail-header">
              <p className="strong">{modal.event.title}</p>
            </div>

            <div className="event-detail-field">
              <span className="event-detail-label">내용</span>
              <p className={`event-detail-content${modal.event.content ? "" : " is-empty"}`}>
                {modal.event.content || "내용이 없습니다."}
              </p>
            </div>

            <hr className="event-detail-divider" />

            <dl className="detail-list">
              <dt>일정 종류</dt>
              <dd>
                <span className={`today-badge ${modal.event.type === "STUDY" ? "study" : "personal"}`}>
                  {modal.event.type === "STUDY" ? "스터디" : "개인"}
                </span>
                {modal.event.type === "STUDY" && (
                  <span className="event-detail-substudy">{modal.event.studyTitle}</span>
                )}
              </dd>
              <dt>일정</dt>
              <dd>
                {formatMonthDay(modal.event.startDate)} ~ {formatMonthDay(modal.event.endDate)}
              </dd>
            </dl>

            {modalError && <p className="error">{modalError}</p>}

            <div className="form-actions">
              <button type="button" className="secondary" onClick={closeModal}>
                닫기
              </button>
              {modal.event.canManage && (
                <>
                  <button type="button" className="secondary" onClick={() => openEdit(modal.event)}>
                    수정
                  </button>
                  <button type="button" disabled={submitting} onClick={() => handleDelete(modal.event)}>
                    {submitting ? "삭제 중..." : "삭제"}
                  </button>
                </>
              )}
            </div>
          </div>
        </Modal>
      )}

      {modal?.kind === "create" && (
        <Modal title="새 일정 추가" onClose={closeModal}>
          <form className="form" onSubmit={handleCreateSubmit}>
            <label>
              제목
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="일정 제목을 입력해요"
                required
                autoFocus
              />
            </label>
            <label>
              내용
              <textarea
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="일정 내용을 입력해요 (선택)"
                rows={3}
              />
            </label>
            <div className="form-grid-2">
              <label>
                시작일
                <span className="date-box">{modal.dateKey.replace(/-/g, ".")}</span>
              </label>
              <label>
                종료일
                <input
                  type="date"
                  value={endDate}
                  min={modal.dateKey}
                  onChange={(e) => setEndDate(e.target.value)}
                  required
                />
              </label>
            </div>
            {endDate && endDate < modal.dateKey && <p className="error">종료일은 시작일 이후여야 해요</p>}
            <label>
              일정 종류
              <div className="type-row" role="radiogroup" aria-label="일정 종류">
                <button
                  type="button"
                  className={`type-btn${scheduleType === "PERSONAL" ? " active" : ""}`}
                  onClick={() => setScheduleType("PERSONAL")}
                >
                  개인
                </button>
                <button
                  type="button"
                  className={`type-btn${scheduleType === "STUDY" ? " active" : ""}`}
                  onClick={() => setScheduleType("STUDY")}
                  disabled={studyOptions.length === 0}
                >
                  스터디
                </button>
              </div>
            </label>
            {scheduleType === "STUDY" &&
              (studyOptions.length === 0 ? (
                <p className="hint">약속을 등록할 수 있는 스터디가 없습니다.</p>
              ) : (
                <label>
                  스터디 선택
                  <select value={studyId} onChange={(e) => setStudyId(e.target.value)} required>
                    <option value="" disabled>
                      스터디를 선택하세요
                    </option>
                    {studyOptions.map((option) => (
                      <option key={option.studyId} value={option.studyId}>
                        {option.title}
                      </option>
                    ))}
                  </select>
                </label>
              ))}
            {modalError && <p className="error">{modalError}</p>}
            <div className="form-actions">
              <button type="button" className="secondary" onClick={closeModal}>
                취소
              </button>
              <button
                type="submit"
                disabled={
                  submitting || (scheduleType === "STUDY" && !studyId) || (!!endDate && endDate < modal.dateKey)
                }
              >
                {submitting ? "추가 중..." : "추가하기"}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {modal?.kind === "edit" && (
        <Modal title="일정 수정" onClose={closeModal}>
          <form className="form" onSubmit={handleUpdateSubmit}>
            <label>
              제목
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="일정 제목을 입력해요"
                required
                autoFocus
              />
            </label>
            <label>
              내용
              <textarea
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="일정 내용을 입력해요 (선택)"
                rows={3}
              />
            </label>
            <div className="form-grid-2">
              <label>
                시작일
                <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
              </label>
              <label>
                종료일
                <input
                  type="date"
                  value={endDate}
                  min={startDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  required
                />
              </label>
            </div>
            {endDate && startDate && endDate < startDate && <p className="error">종료일은 시작일 이후여야 해요</p>}
            {modalError && <p className="error">{modalError}</p>}
            <div className="form-actions">
              <button type="button" className="secondary" onClick={closeModal}>
                취소
              </button>
              <button type="submit" disabled={submitting || (!!endDate && !!startDate && endDate < startDate)}>
                {submitting ? "수정 중..." : "수정하기"}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
