const BASE_URL = 'http://10.0.2.2:8080';

async function post<T>(path: string, body: object): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  const text = await res.text();
  console.log('RAW 응답:', text);
  const parsed = text ? JSON.parse(text) : {};
  return 'data' in parsed ? parsed.data : parsed;
}

export function googleLogin(idToken: string) {
  return post<{accessToken: string; refreshToken: string}>('/api/auth/google', {idToken});
}

export function login(email: string, password: string) {
  return post<{accessToken: string; refreshToken: string}>('/api/auth/login', {email, password});
}

export function signup(email: string, password: string, nickname: string, passwordConfirm: string) {
  return post<void>('/api/auth/signup', {email, password, nickname, passwordConfirm});
}

export function refreshAccessToken(refreshToken: string) {
  return post<{accessToken: string}>('/api/auth/refresh', {refreshToken});
}
