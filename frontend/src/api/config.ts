import {API_BASE_URL as ENV_API_BASE_URL} from '@env';

// 앱이 요청을 보낼 서버 주소. frontend/.env의 API_BASE_URL로 바꾼다(배포 서버 연결 시 필수).
// 값이 없으면 안드로이드 에뮬레이터에서 같은 PC의 서버(localhost:8080)를 가리키는 주소를 쓴다.
export const API_BASE_URL: string = (ENV_API_BASE_URL || 'http://10.0.2.2:8080').replace(/\/+$/, '');
