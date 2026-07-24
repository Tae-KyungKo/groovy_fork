const TOKEN_KEY = "groovy_access_token";

let token: string | null = localStorage.getItem(TOKEN_KEY);

export function getToken(): string | null {
  return token;
}

export function setToken(newToken: string): void {
  token = newToken;
  localStorage.setItem(TOKEN_KEY, newToken);
}

export function clearToken(): void {
  token = null;
  localStorage.removeItem(TOKEN_KEY);
}
