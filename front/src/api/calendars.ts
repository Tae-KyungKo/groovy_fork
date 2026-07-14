import type { CalendarEvent } from "../types";
import { apiFetch, USE_MOCK } from "./client";
import { calendarEvents, delay, nextId, persist } from "./mockStore";

export interface PersonalEventPayload {
  title: string;
  date: string;
}

export async function listCalendarEvents(): Promise<CalendarEvent[]> {
  if (USE_MOCK) {
    await delay();
    return [...calendarEvents].sort((a, b) => a.date.localeCompare(b.date));
  }
  return apiFetch<CalendarEvent[]>("/api/calendars");
}

export async function addPersonalEvent(payload: PersonalEventPayload): Promise<CalendarEvent> {
  if (USE_MOCK) {
    await delay();
    const event: CalendarEvent = { id: nextId(), type: "PERSONAL", ...payload };
    calendarEvents.push(event);
    persist();
    return event;
  }
  return apiFetch<CalendarEvent>("/api/calendars", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
