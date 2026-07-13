import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { addPersonalEvent, listCalendarEvents } from "../api/calendars";
import type { CalendarEvent } from "../types";

export function CalendarPage() {
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [title, setTitle] = useState("");
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));

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

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title) return;
    await addPersonalEvent({ title, date });
    setTitle("");
    refresh();
  }

  return (
    <div className="page">
      <h1>캘린더</h1>
      <form className="card form form-inline" onSubmit={handleSubmit}>
        <label>
          제목
          <input value={title} onChange={(e) => setTitle(e.target.value)} required />
        </label>
        <label>
          날짜
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
        </label>
        <button type="submit">일정 추가</button>
      </form>

      {loading ? (
        <p className="page-loading">불러오는 중...</p>
      ) : (
        <ul className="list">
          {events.map((event) => (
            <li key={event.id} className="card list-item">
              <div>
                <p className="strong">{event.title}</p>
                <time>{event.date}</time>
              </div>
              <span className={`status status-${event.type.toLowerCase()}`}>
                {event.type === "STUDY" ? event.studyTitle ?? "스터디 일정" : "개인 일정"}
              </span>
            </li>
          ))}
          {events.length === 0 && <p className="empty">등록된 일정이 없습니다.</p>}
        </ul>
      )}
    </div>
  );
}
