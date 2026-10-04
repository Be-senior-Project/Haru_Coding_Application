// 회원가입/로그인 입력 규칙. 백엔드 SignupRequest의 검증과 반드시 같이 바꾼다.

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const NICKNAME_MIN = 2;
export const NICKNAME_MAX = 12;
export const PASSWORD_MIN = 8;
// BCrypt는 72바이트를 넘는 입력을 거부한다. 여유를 두고 64자로 자른다.
export const PASSWORD_MAX = 64;

export function validateEmail(email: string): string | null {
  const v = email.trim();
  if (!v) {
    return '이메일을 입력해주세요.';
  }
  if (!EMAIL_RE.test(v)) {
    return '이메일 형식이 올바르지 않습니다. (예: haru@coding.com)';
  }
  return null;
}

export function validateNickname(nickname: string): string | null {
  const len = nickname.trim().length;
  if (len === 0) {
    return '닉네임을 입력해주세요.';
  }
  if (len < NICKNAME_MIN || len > NICKNAME_MAX) {
    return `닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자로 입력해주세요.`;
  }
  return null;
}

export type PasswordRule = {label: string; ok: boolean};

/** 입력 중에 체크리스트로 보여줄 항목. 모두 ok여야 통과. */
export function passwordRules(pw: string): PasswordRule[] {
  return [
    {label: '영문 포함', ok: /[A-Za-z]/.test(pw)},
    {label: '숫자 포함', ok: /\d/.test(pw)},
    {label: `${PASSWORD_MIN}자 이상`, ok: pw.length >= PASSWORD_MIN},
  ];
}

export function validatePassword(pw: string): string | null {
  if (!pw) {
    return '비밀번호를 입력해주세요.';
  }
  if (/\s/.test(pw)) {
    return '비밀번호에 공백은 사용할 수 없습니다.';
  }
  if (pw.length > PASSWORD_MAX) {
    return `비밀번호는 ${PASSWORD_MAX}자 이하로 입력해주세요.`;
  }
  if (!passwordRules(pw).every(r => r.ok)) {
    return `영문과 숫자를 포함해 ${PASSWORD_MIN}자 이상 입력해주세요.`;
  }
  return null;
}
