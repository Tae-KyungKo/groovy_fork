import type {
  Application,
  AppNotification,
  CalendarEvent,
  Study,
  Tag,
  User,
} from "../types";

// In-memory + localStorage 목업 백엔드. 실제 백엔드가 준비되면 각 api/*.ts 에서
// USE_MOCK 분기만 제거하고 apiFetch 호출로 교체하면 됩니다.
// localStorage에 저장하는 이유: 새로고침해도 로그인/스터디 데이터가 유지되어야
// 실제 백엔드처럼 자연스럽게 동작을 확인할 수 있기 때문입니다.

export const delay = (ms = 300) => new Promise((resolve) => setTimeout(resolve, ms));

const STORE_KEY = "groovy_mock_store_v2";

interface StoreShape {
  idCounter: number;
  users: (User & { password: string })[];
  studies: Study[];
  applications: Application[];
  waitingList: Record<string, string[]>;
  calendarEvents: CalendarEvent[];
  myTagPrefs: Record<string, number[]>;
}

function seed(): StoreShape {
  return {
    idCounter: 100,
    users: [{ id: "u1", email: "demo@groovy.dev", password: "password123", name: "데모유저" }],
    studies: [
      {
        id: "s1",
        title: "알고리즘 스터디 3기",
        description: "매주 백준 5문제씩 풀고 리뷰합니다.",
        leaderId: "u1",
        leaderName: "데모유저",
        capacity: 6,
        memberCount: 3,
        tagIds: [1, 4],
        meetingStartTime: "2026-07-15T19:00:00",
        meetingEndTime: "2026-07-15T21:00:00",
        createdAt: "2026-07-01T00:00:00Z",
      },
      {
        id: "s2",
        title: "프론트엔드 딥다이브",
        description: "React 내부 동작과 렌더링 성능을 공부합니다.",
        leaderId: "u2",
        leaderName: "다른유저",
        capacity: 5,
        memberCount: 5,
        tagIds: [2],
        meetingStartTime: "2026-07-16T20:00:00",
        meetingEndTime: "2026-07-16T22:00:00",
        createdAt: "2026-07-03T00:00:00Z",
      },
    ],
    applications: [],
    waitingList: {},
    calendarEvents: [
      {
        id: "e1",
        title: "알고리즘 스터디 정기모임",
        date: "2026-07-15",
        studyId: "s1",
        studyTitle: "알고리즘 스터디 3기",
        type: "STUDY",
      },
    ],
    myTagPrefs: {},
  };
}

function load(): StoreShape {
  const raw = localStorage.getItem(STORE_KEY);
  if (!raw) return seed();
  try {
    return JSON.parse(raw) as StoreShape;
  } catch {
    return seed();
  }
}

const store = load();

export const tags: Tag[] = [
  { id: 1, name: "알고리즘" },
  { id: 2, name: "프론트엔드" },
  { id: 3, name: "백엔드" },
  { id: 4, name: "면접준비" },
  { id: 5, name: "토익" },
  { id: 6, name: "독서" },
];

export const users = store.users;
export const studies = store.studies;
export const applications = store.applications;
export const waitingList = store.waitingList;
export const calendarEvents = store.calendarEvents;
export const notifications: AppNotification[] = [];

export function persist() {
  localStorage.setItem(
    STORE_KEY,
    JSON.stringify({
      idCounter: store.idCounter,
      users,
      studies,
      applications,
      waitingList,
      calendarEvents,
      myTagPrefs: store.myTagPrefs,
    } satisfies StoreShape),
  );
}

export function nextId() {
  const id = String(store.idCounter++);
  persist();
  return id;
}

export function getMyTagIds(userId: string): number[] {
  return store.myTagPrefs[userId] ?? [];
}

export function setMyTagIds(userId: string, tagIds: number[]) {
  store.myTagPrefs[userId] = tagIds;
}

const SESSION_KEY = "groovy_mock_session_user_id";

let currentUserId: string | null = localStorage.getItem(SESSION_KEY);

export function getCurrentUserId() {
  return currentUserId;
}
export function setCurrentUserId(id: string | null) {
  currentUserId = id;
  if (id) localStorage.setItem(SESSION_KEY, id);
  else localStorage.removeItem(SESSION_KEY);
}

export function requireUser(): User {
  const user = users.find((u) => u.id === currentUserId);
  if (!user) throw new Error("로그인이 필요합니다.");
  const { password: _password, ...rest } = user;
  void _password;
  return rest;
}
