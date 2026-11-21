declare module '@env' {
  export const GOOGLE_WEB_CLIENT_ID: string;
  // 비어 있을 수 있다(src/api/config.ts에서 기본 주소로 대체)
  export const API_BASE_URL: string | undefined;
}
