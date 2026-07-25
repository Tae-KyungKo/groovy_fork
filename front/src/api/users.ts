import type { MyApplication, Study } from "../types";
import { apiFetch } from "./client";

// 마이페이지 "내가 만든 스터디" 목록.
export async function getMyStudies(): Promise<Study[]> {
  return apiFetch<Study[]>("/api/users/me/studies");
}

// 마이페이지 "참여 중인 스터디 / 신청 내역" 목록.
export async function getMyApplications(): Promise<MyApplication[]> {
  return apiFetch<MyApplication[]>("/api/users/me/applications");
}
