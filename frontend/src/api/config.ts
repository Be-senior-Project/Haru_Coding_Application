import {API_BASE_URL as ENV_API_BASE_URL} from '@env';

// 백엔드 주소. frontend/.env의 API_BASE_URL이 있으면 그 값을 쓴다.
// 비어 있으면 개발 빌드는 안드로이드 에뮬레이터 → 호스트 PC(10.0.2.2),
// 릴리스 빌드(APK)는 운영 서버로 붙는다.
const DEFAULT_API_BASE_URL = __DEV__
  ? 'http://10.0.2.2:8080'
  : 'https://haru.careerpilot.kr';

export const API_BASE_URL = (ENV_API_BASE_URL || DEFAULT_API_BASE_URL).replace(
  /\/+$/,
  '',
);
