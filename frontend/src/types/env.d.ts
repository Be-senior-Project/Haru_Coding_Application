declare module '@env' {
  export const GOOGLE_WEB_CLIENT_ID: string;
  /** 비어 있으면 src/api/config.ts의 기본값(__DEV__ 기준)을 쓴다. */
  export const API_BASE_URL: string | undefined;
}
