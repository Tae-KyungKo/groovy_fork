export interface User {
  id: string;
  email: string;
  name: string;
}

export interface Tag {
  id: number;
  name: string;
}

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
