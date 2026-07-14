import type { CalendarEvent, CalendarStudyOption } from "../types";
import { apiFetch, USE_MOCK } from "./client";
import { applications, calendarEvents, delay, nextId, persist, requireUser, studies } from "./mockStore";

export interface CalendarEventPayload {
  title: string;
  date: string;
  // 지정하면 개인 일정이 아니라 해당 스터디 멤버 전원과 공유되는 스터디 약속으로 등록된다.
  studyId?: string;
}

export async function listCalendarEvents(): Promise<CalendarEvent[]> {
  if (USE_MOCK) {
    await delay();
    return [...calendarEvents].sort((a, b) => a.date.localeCompare(b.date));
  }
  return apiFetch<CalendarEvent[]>("/api/calendars");
}

export async function listMyStudyOptions(): Promise<CalendarStudyOption[]> {
  if (USE_MOCK) {
    await delay();
    const user = requireUser();
    return studies
      .filter(
        (study) =>
          study.leaderId === user.id ||
          applications.some(
            (app) => app.studyId === study.id && app.userId === user.id && app.status === "APPROVED",
          ),
      )
      .map((study) => ({ studyId: study.id, title: study.title }));
  }
  return apiFetch<CalendarStudyOption[]>("/api/calendars/studies");
}

export async function addCalendarEvent(payload: CalendarEventPayload): Promise<CalendarEvent> {
  if (USE_MOCK) {
    await delay();
    const study = payload.studyId ? studies.find((s) => s.id === payload.studyId) : undefined;
    const event: CalendarEvent = study
      ? { id: nextId(), title: payload.title, date: payload.date, studyId: study.id, studyTitle: study.title, type: "STUDY" }
      : { id: nextId(), title: payload.title, date: payload.date, type: "PERSONAL" };
    calendarEvents.push(event);
    persist();
    return event;
  }
  return apiFetch<CalendarEvent>("/api/calendars", {
    method: "POST",
    // studyId는 select 값이라 항상 문자열이므로, 서버의 숫자 타입 관용적 변환에 기대지 않도록 명시적으로 숫자로 변환한다.
    body: JSON.stringify({
      title: payload.title,
      date: payload.date,
      studyId: payload.studyId ? Number(payload.studyId) : undefined,
    }),
  });
}
