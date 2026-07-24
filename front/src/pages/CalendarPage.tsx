import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { addCalendarEvent, listCalendarEvents, listMyStudyOptions } from "../api/calendars";
import { Modal } from "../components/Modal";
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
  const [modalDate, setModalDate] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [endDate, setEndDate] = useState("");
  const [scheduleType, setScheduleType] = useState<ScheduleType>("PERSONAL");
  const [studyId, setStudyId] = useState("");
  const [submitting, setSubmitting] = useState(false);

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

  function openModal(dateKey: string) {
    setModalDate(dateKey);
    setTitle("");
    setEndDate(dateKey);
    setScheduleType("PERSONAL");
    setStudyId("");
  }

  function closeModal() {
    setModalDate(null);
    setTitle("");
    setEndDate("");
    setScheduleType("PERSONAL");
    setStudyId("");
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title || !modalDate) return;
    if (scheduleType === "STUDY" && !studyId) return;
    if (endDate && endDate < modalDate) return;
    setSubmitting(true);
    try {
      await addCalendarEvent({
        title,
        startDate: modalDate,
        endDate: endDate || modalDate,
        studyId: scheduleType === "STUDY" ? studyId : undefined,
      });
      refresh();
      closeModal();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>캘린더</h1>
        <div className="button-row">
          <button type="button" className="secondary" onClick={() => setCurrentMonth((m) => addMonths(m, -1))}>
            &lt;
          </button>
          <span className="strong month-label">
            {currentMonth.getFullYear()}년 {currentMonth.getMonth() + 1}월
          </span>
          <button type="button" className="secondary" onClick={() => setCurrentMonth((m) => addMonths(m, 1))}>
            &gt;
          </button>
          <button type="button" className="secondary" onClick={() => setCurrentMonth(startOfMonth(new Date()))}>
            오늘
          </button>
        </div>
      </div>

      {loading ? (
        <p className="page-loading">불러오는 중...</p>
      ) : (
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
                  onClick={() => openModal(dateKey)}
                  aria-label={`${dateKey} 일정 추가${totalCount ? `, 일정 ${totalCount}건` : ""}`}
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
                          {showLabel ? label : " "}
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
      )}

      {modalDate && (
        <Modal
          title={endDate && endDate !== modalDate ? `${modalDate} ~ ${endDate} 일정 추가` : `${modalDate} 일정 추가`}
          onClose={closeModal}
        >
          <form className="form" onSubmit={handleSubmit}>
            <label>
              제목
              <input value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
            </label>
            <label>
              종료일
              <input
                type="date"
                value={endDate}
                min={modalDate}
                onChange={(e) => setEndDate(e.target.value)}
                required
              />
            </label>
            {endDate && endDate < modalDate && <p className="hint">종료일은 시작일보다 빠를 수 없습니다.</p>}
            <div className="button-row" role="radiogroup" aria-label="일정 종류">
              <button
                type="button"
                className={scheduleType === "PERSONAL" ? undefined : "secondary"}
                onClick={() => setScheduleType("PERSONAL")}
              >
                개인 일정
              </button>
              <button
                type="button"
                className={scheduleType === "STUDY" ? undefined : "secondary"}
                onClick={() => setScheduleType("STUDY")}
                disabled={studyOptions.length === 0}
              >
                스터디 약속
              </button>
            </div>
            {scheduleType === "STUDY" &&
              (studyOptions.length === 0 ? (
                <p className="hint">약속을 등록할 수 있는 스터디가 없습니다.</p>
              ) : (
                <label>
                  스터디
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
            <button
              type="submit"
              disabled={
                submitting || (scheduleType === "STUDY" && !studyId) || (!!endDate && endDate < modalDate)
              }
            >
              {submitting ? "추가 중..." : "일정 추가"}
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}
