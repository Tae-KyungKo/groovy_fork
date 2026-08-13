import type { CalendarEvent, CalendarStudyOption } from "../types";
import { apiFetch } from "./client";

export interface CalendarEventPayload {
  title: string;
  content?: string;
  startDate: string;
  // 지정하지 않으면 startDate와 같은 하루짜리 일정으로 등록된다.
  endDate?: string;
  // 지정하면 개인 일정이 아니라 해당 스터디 멤버 전원과 공유되는 스터디 약속으로 등록된다.
  studyId?: string;
}

export interface CalendarEventUpdatePayload {
  title: string;
  content?: string;
  startDate: string;
  endDate?: string;
}

export async function listCalendarEvents(): Promise<CalendarEvent[]> {
  return apiFetch<CalendarEvent[]>("/api/calendars");
}

export async function listMyStudyOptions(): Promise<CalendarStudyOption[]> {
  return apiFetch<CalendarStudyOption[]>("/api/calendars/studies");
}

export async function addCalendarEvent(payload: CalendarEventPayload): Promise<CalendarEvent> {
  const endDate = payload.endDate ?? payload.startDate;
  return apiFetch<CalendarEvent>("/api/calendars", {
    method: "POST",
    // studyId는 select 값이라 항상 문자열이므로, 서버의 숫자 타입 관용적 변환에 기대지 않도록 명시적으로 숫자로 변환한다.
    body: JSON.stringify({
      title: payload.title,
      content: payload.content,
      startDate: payload.startDate,
      endDate,
      studyId: payload.studyId ? Number(payload.studyId) : undefined,
    }),
  });
}

export async function getCalendarEvent(calendarId: string): Promise<CalendarEvent> {
  return apiFetch<CalendarEvent>(`/api/calendars/${calendarId}`);
}

export async function updateCalendarEvent(
  calendarId: string,
  payload: CalendarEventUpdatePayload,
): Promise<CalendarEvent> {
  const endDate = payload.endDate ?? payload.startDate;
  return apiFetch<CalendarEvent>(`/api/calendars/${calendarId}`, {
    method: "PUT",
    body: JSON.stringify({
      title: payload.title,
      content: payload.content,
      startDate: payload.startDate,
      endDate,
    }),
  });
}

export async function deleteCalendarEvent(calendarId: string): Promise<void> {
  return apiFetch<void>(`/api/calendars/${calendarId}`, { method: "DELETE" });
}
