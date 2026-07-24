import type { User } from "../types";
import { apiFetch, USE_MOCK } from "./client";
import { delay, nextId, persist, setCurrentUserId, users, requireUser } from "./mockStore";
import { clearToken, setToken } from "./tokenStore";

export interface SignupPayload {
  email: string;
  password: string;
  name: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export async function signup(payload: SignupPayload): Promise<User> {
  if (USE_MOCK) {
    await delay();
    if (users.some((u) => u.email === payload.email)) {
      throw new Error("이미 가입된 이메일입니다.");
    }
    const user = { id: nextId(), ...payload };
    users.push(user);
    persist();
    const { password: _password, ...rest } = user;
    void _password;
    return rest;
  }
  return apiFetch<User>("/api/auth/signup", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function login(payload: LoginPayload): Promise<User> {
  if (USE_MOCK) {
    await delay();
    const user = users.find((u) => u.email === payload.email && u.password === payload.password);
    if (!user) throw new Error("이메일 또는 비밀번호가 올바르지 않습니다.");
    setCurrentUserId(user.id);
    const { password: _password, ...rest } = user;
    void _password;
    return rest;
  }
  const { accessToken } = await apiFetch<{ accessToken: string }>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
  setToken(accessToken);
  return me();
}

export async function logout(): Promise<void> {
  if (USE_MOCK) {
    await delay(100);
    setCurrentUserId(null);
    return;
  }
  await apiFetch<void>("/api/auth/logout", { method: "POST" });
  clearToken();
}

export async function me(): Promise<User> {
  if (USE_MOCK) {
    await delay(100);
    return requireUser();
  }
  return apiFetch<User>("/api/users/me");
}
