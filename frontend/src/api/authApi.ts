const BASE_URL = 'http://10.0.2.2:8080';

async function post<T>(path: string, body: object): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new AuthApiError(res.status, extractMessage(text) ?? `HTTP ${res.status}`);
  }
  const parsed = text ? JSON.parse(text) : {};
  return 'data' in parsed ? parsed.data : parsed;
}

/** 화면에서 상태 코드로 분기(예: 409 → 이메일 칸 아래 표시)할 수 있도록 status를 같이 싣는다. */
export class AuthApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

// 서버는 {"success":false,"message":"..."} 형태로 에러를 준다.
// 본문을 그대로 던지면 알림창에 JSON 문자열이 그대로 보였다.
function extractMessage(text: string): string | null {
  try {
    const parsed = JSON.parse(text);
    return typeof parsed?.message === 'string' ? parsed.message : null;
  } catch {
    return null;
  }
}

type Tokens = {accessToken: string; refreshToken: string};

export function googleLogin(idToken: string) {
  return post<Tokens>('/api/auth/google', {idToken});
}

export function login(email: string, password: string) {
  return post<Tokens>('/api/auth/login', {email, password});
}

/** 가입 응답에 토큰이 같이 오므로 별도 로그인 호출 없이 바로 쓸 수 있다. */
export function signup(email: string, password: string, nickname: string, passwordConfirm: string) {
  return post<Tokens>('/api/auth/signup', {email, password, nickname, passwordConfirm});
}

export function refreshAccessToken(refreshToken: string) {
  return post<{accessToken: string}>('/api/auth/refresh', {refreshToken});
}

/**
 * 서버의 refresh token을 폐기한다.
 * 이 호출이 없으면 앱에서 토큰을 지워도 DB에 refresh token이 남아
 * 그 값을 가진 쪽은 계속 액세스 토큰을 재발급받을 수 있다.
 */
export async function logout(accessToken: string): Promise<void> {
  const res = await fetch(`${BASE_URL}/api/auth/logout`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
}
