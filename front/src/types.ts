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

// 마이페이지 "참여 중인 스터디 / 신청 내역"용. 내가 신청한 스터디 정보를 스터디명과 함께 보여준다.
export interface MyApplication {
  id: string;
  studyId: string;
  studyTitle: string;
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
  startDate: string;
  endDate: string;
  studyId?: string;
  studyTitle?: string;
  type: "PERSONAL" | "STUDY";
}

// 캘린더에서 "스터디 약속" 등록 시 고를 수 있는, 내가 속한(방장이거나 승인된) 스터디 목록.
export interface CalendarStudyOption {
  studyId: string;
  title: string;
}

export interface AppNotification {
  id: string;
  message: string;
  createdAt: string;
  read: boolean;
}
