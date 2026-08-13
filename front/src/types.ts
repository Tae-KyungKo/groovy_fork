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
  // 요일·시각은 백엔드에서 선택 항목이라 비어 있을 수 있다.
  meetingDays: DayOfWeek[];
  meetingStartTime: string | null;
  meetingEndTime: string | null;
  level: number;
  expPoint: number;
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

export interface Memoir {
  id: string;
  studyId: string;
  studyTitle: string;
  authorId: string;
  authorName: string;
  title: string;
  content: string;
  commentCount: number;
  likeCount: number;
  // 현재 로그인한 뷰어가 이 회고록에 좋아요를 눌렀는지. 비회원 조회 시 항상 false.
  liked: boolean;
  // 회고록이 연결된 스터디 팀의 레벨/경험치(회고록·댓글 작성 시 누적).
  studyLevel: number;
  studyExpPoint: number;
  createdAt: string;
  updatedAt: string;
}

export interface MemoirComment {
  id: string;
  memoirId: string;
  authorId: string;
  authorName: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

// 회고록 작성 시 연결할 스터디를 고르기 위한, 내가 속한(방장이거나 승인된) 스터디 목록.
export interface MemoirStudyOption {
  studyId: string;
  title: string;
}

export type MemoirSort = "latest" | "popular";
