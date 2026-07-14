export interface User {
  id: string;
  email: string;
  name: string;
}

export interface Tag {
  id: number;
  name: string;
}

export const DAYS_OF_WEEK = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const;
export type DayOfWeek = (typeof DAYS_OF_WEEK)[number];
export const DAY_LABELS: Record<DayOfWeek, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일",
};

export type ApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface Application {
  id: string;
  studyId: string;
  userId: string;
  userName: string;
  status: ApplicationStatus;
  appliedAt: string;
}

export interface Study {
  id: string;
  title: string;
  description: string;
  leaderId: string;
  leaderName: string;
  capacity: number;
  memberCount: number;
  tagIds: number[];
  // 요일 반복 일정: meetingDays(요일 목록) + meetingStartTime/meetingEndTime("HH:mm").
  // 백엔드는 아직 LocalDateTime 필드만 있어 요일 개념이 없음 (프론트 우선 반영, 백엔드 연동은 별도 작업).
  meetingDays: DayOfWeek[];
  meetingStartTime: string;
  meetingEndTime: string;
  createdAt: string;
}

export interface StudyMatch {
  study: Study;
  matchedTagCount: number;
  matchScore: number;
}

export interface PageResponse<T> {
  content: T[];
  number: number;
  totalPages: number;
  totalElements: number;
  size: number;
}

export interface WaitingPosition {
  position: number;
  totalWaiting: number;
}

export interface CalendarEvent {
  id: string;
  title: string;
  date: string;
  studyId?: string;
  studyTitle?: string;
  type: "PERSONAL" | "STUDY";
}

export interface AppNotification {
  id: string;
  message: string;
  createdAt: string;
  read: boolean;
}
