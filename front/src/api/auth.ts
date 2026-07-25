import type { User } from "../types";
import { apiFetch } from "./client";
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
  return apiFetch<User>("/api/auth/signup", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function login(payload: LoginPayload): Promise<User> {
  const { accessToken } = await apiFetch<{ accessToken: string }>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
  setToken(accessToken);
  return me();
}

export async function logout(): Promise<void> {
  await apiFetch<void>("/api/auth/logout", { method: "POST" });
  clearToken();
}

export async function me(): Promise<User> {
  return apiFetch<User>("/api/users/me");
}
