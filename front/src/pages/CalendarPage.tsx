import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { addPersonalEvent, listCalendarEvents } from "../api/calendars";
import { Modal } from "../components/Modal";
import type { CalendarEvent } from "../types";
import { addMonths, buildMonthGrid, startOfMonth, toDateKey } from "../utils/date";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const MAX_VISIBLE_EVENTS = 3;

export function CalendarPage() {
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()));
  const [modalDate, setModalDate] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    refresh();
  }, []);

  function refresh() {
    setLoading(true);
    listCalendarEvents().then((list) => {
      setEvents(list);
      setLoading(false);
    });
  }

  const eventsByDate = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>();
    for (const event of events) {
      const list = map.get(event.date) ?? [];
      list.push(event);
      map.set(event.date, list);
    }
    return map;
  }, [events]);

  const gridDays = useMemo(() => buildMonthGrid(currentMonth), [currentMonth]);
  const todayKey = toDateKey(new Date());

  function openModal(dateKey: string) {
    setModalDate(dateKey);
    setTitle("");
  }

  function closeModal() {
    setModalDate(null);
    setTitle("");
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title || !modalDate) return;
    setSubmitting(true);
    try {
      await addPersonalEvent({ title, date: modalDate });
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
              const dayEvents = eventsByDate.get(dateKey) ?? [];
              const visible = dayEvents.slice(0, MAX_VISIBLE_EVENTS);
              const hiddenCount = dayEvents.length - visible.length;
              const isOtherMonth = date.getMonth() !== currentMonth.getMonth();
              const isToday = dateKey === todayKey;

              return (
                <button
                  key={dateKey}
                  type="button"
                  className={`calendar-cell${isOtherMonth ? " other-month" : ""}${isToday ? " today" : ""}`}
                  onClick={() => openModal(dateKey)}
                  aria-label={`${dateKey} 일정 추가${dayEvents.length ? `, 일정 ${dayEvents.length}건` : ""}`}
                >
                  <span className="calendar-cell-date">{date.getDate()}</span>
                  <span className="calendar-cell-events">
                    {visible.map((event) => (
                      <span key={event.id} className={`calendar-event calendar-event-${event.type.toLowerCase()}`}>
                        {event.type === "STUDY" ? event.studyTitle ?? event.title : event.title}
                      </span>
                    ))}
                    {hiddenCount > 0 && <span className="calendar-event-more">+{hiddenCount}개</span>}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      {modalDate && (
        <Modal title={`${modalDate} 일정 추가`} onClose={closeModal}>
          <form className="form" onSubmit={handleSubmit}>
            <label>
              제목
              <input value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
            </label>
            <button type="submit" disabled={submitting}>
              {submitting ? "추가 중..." : "일정 추가"}
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}
