import AsyncStorage from '@react-native-async-storage/async-storage';

const BASE_URL = 'http://10.0.2.2:8080';

// ── 토큰 자동 갱신 ───────────────────────────────────────────────
// 401/403(인증 만료 추정) 시 refreshToken으로 액세스 토큰 재발급 후 1회 재시도.
// 동시에 여러 요청이 실패해도 갱신은 한 번만 수행(single-flight).
let refreshPromise: Promise<string | null> | null = null;

async function doRefresh(): Promise<string | null> {
  const refreshToken = await AsyncStorage.getItem('refreshToken');
  if (!refreshToken) {
    return null;
  }
  try {
    // 백엔드 /api/auth/refresh 는 refreshToken 을 Authorization 헤더로 받음
    const res = await fetch(`${BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${refreshToken}`,
      },
    });
    if (!res.ok) {
      return null;
    }
    const json = await res.json();
    const data = json?.data ?? json;
    if (!data?.accessToken) {
      return null;
    }
    const pairs: [string, string][] = [['accessToken', data.accessToken]];
    if (data.refreshToken) {
      pairs.push(['refreshToken', data.refreshToken]); // 백엔드가 리프레시 토큰도 회전시키면 갱신
    }
    await AsyncStorage.multiSet(pairs);
    return data.accessToken;
  } catch {
    return null;
  }
}

function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  requireAuth = true,
  retried = false,
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (requireAuth) {
    const token = await AsyncStorage.getItem('accessToken');
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const res = await fetch(`${BASE_URL}${path}`, {...options, headers});

  // 인증 만료 추정(401/403) → 토큰 한 번 갱신 후 재시도
  if ((res.status === 401 || res.status === 403) && requireAuth && !retried) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      return request<T>(path, options, requireAuth, true);
    }
    // 갱신 실패(리프레시 토큰도 만료 등) → 세션 정리. 화면은 토큰 없음을 보고 로그인 유도.
    await AsyncStorage.multiRemove(['accessToken', 'refreshToken']);
  }

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    const error = new Error(text || `요청에 실패했어요 (HTTP ${res.status})`) as Error & {status?: number};
    error.status = res.status;
    throw error;
  }

  // 204 No Content / 빈 본문(DELETE 등)은 파싱하지 않고 통과
  const text = await res.text();
  if (!text) {
    return undefined as T;
  }
  const json = JSON.parse(text);
  return json.data ?? json;
}

export const api = {
  get: <T>(path: string, auth = true) =>
    request<T>(path, {method: 'GET'}, auth),

  post: <T>(path: string, body: object, auth = true) =>
    request<T>(path, {method: 'POST', body: JSON.stringify(body)}, auth),

  patch: <T>(path: string, body: object, auth = true) =>
    request<T>(path, {method: 'PATCH', body: JSON.stringify(body)}, auth),

  del: <T>(path: string, auth = true) =>
    request<T>(path, {method: 'DELETE'}, auth),
};
