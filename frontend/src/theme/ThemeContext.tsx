import React, {createContext, useContext, useEffect, useState} from 'react';
import {useColorScheme} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

export type Colors = {
  bg: string;
  card: string;
  text: string;
  subText: string;
  border: string;
  filterInactive: string;
  primary: string;      // 메인 보라색 (버튼/강조)
  primarySoft: string;  // 연한 보라 배경 (배지/아이콘 박스)
  onPrimary: string;    // primary 배경 위에 올라가는 글자/아이콘

  // ── 의미가 고정된 색 ─────────────────────────────────────────
  // *Soft는 같은 의미의 연한 배경(배지/카드). 다른 용도로 재사용 금지.
  success: string;      // 정답 · 성공 · 증가
  successSoft: string;
  danger: string;       // 오답 · 실패 · 삭제
  dangerSoft: string;
  warning: string;      // 주의 · 팁 · 중급 난이도
  warningSoft: string;
  streak: string;       // 연속 학습 · 불꽃
  streakSoft: string;
  info: string;         // 정보 · 중립 강조
  infoSoft: string;

  isDark: boolean;
};

// 리그 티어 색 — 금속 색이라 라이트/다크 공통
export const TIER_COLOR: Record<string, string> = {
  BRONZE: '#B08D57',
  SILVER: '#9AA5B1',
  GOLD: '#F5B301',
  PLATINUM: '#26C281',
  DIAMOND: '#3B82F6',
};

export function tierColor(tier?: string): string {
  return TIER_COLOR[tier?.toUpperCase() ?? ''] ?? TIER_COLOR.BRONZE;
}

export type FontSizeKey = 'small' | 'medium' | 'large';

export const FONT_SCALE: Record<FontSizeKey, number> = {
  small: 0.85,
  medium: 1.0,
  large: 1.2,
};

const light: Colors = {
  bg: '#F5F6FB',
  card: '#FFFFFF',
  text: '#1A1A2E',
  subText: '#8A8AA0',
  border: '#ECECF3',
  filterInactive: '#E0E0E0',
  primary: '#6C5CE7',
  primarySoft: '#EEEBFF',
  onPrimary: '#FFFFFF',

  success: '#26C281',
  successSoft: '#E6F8F0',
  danger: '#F44336',
  dangerSoft: '#FFEBEE',
  warning: '#F5B301',
  warningSoft: '#FFF3E0',
  streak: '#FF6B35',
  streakSoft: '#FFF1E6',
  info: '#3B82F6',
  infoSoft: '#E7F1FE',

  isDark: false,
};

// 다크에서는 전경색을 밝게 올리고, *Soft는 어두운 배경으로 뒤집는다.
const dark: Colors = {
  bg: '#0F1117',
  card: '#1E1E2E',
  text: '#F0F0F5',
  subText: '#9E9E9E',
  border: '#2E2E3E',
  filterInactive: '#2A2A3A',
  primary: '#8B7BF0',
  primarySoft: '#26233A',
  onPrimary: '#FFFFFF',

  success: '#3DD69B',
  successSoft: '#14332A',
  danger: '#FF6B6B',
  dangerSoft: '#3A1E20',
  warning: '#FFC93C',
  warningSoft: '#332711',
  streak: '#FF8A5C',
  streakSoft: '#3A2118',
  info: '#5B9BF8',
  infoSoft: '#16263F',

  isDark: true,
};

type ThemeContextType = {
  colors: Colors;
  isDark: boolean;
  toggleTheme: () => void;
  fontScale: number;
  fontSizeKey: FontSizeKey;
  setFontSize: (key: FontSizeKey) => void;
};

// 기본값(컨텍스트 미초기화 시) — 실제로는 ThemeProvider가 덮어씀
const ThemeContext = createContext<ThemeContextType>({
  colors: light,
  isDark: false,
  toggleTheme: () => {},
  fontScale: 1.0,
  fontSizeKey: 'medium',
  setFontSize: () => {},
});

export function ThemeProvider({children}: {children: React.ReactNode}) {
  const systemScheme = useColorScheme();
  const [isDark, setIsDark] = useState(systemScheme === 'dark');
  const [fontSizeKey, setFontSizeKey] = useState<FontSizeKey>('medium');

  useEffect(() => {
    Promise.all([
      AsyncStorage.getItem('darkMode'),
      AsyncStorage.getItem('fontSize'),
    ]).then(([dark, font]) => {
      if (dark !== null) {setIsDark(dark === '1');}
      if (font !== null) {setFontSizeKey(font as FontSizeKey);}
    });
  }, []);

  const toggleTheme = () => {
    setIsDark(prev => {
      const next = !prev;
      AsyncStorage.setItem('darkMode', next ? '1' : '0');
      return next;
    });
  };

  const setFontSize = (key: FontSizeKey) => {
    setFontSizeKey(key);
    AsyncStorage.setItem('fontSize', key);
  };

  return (
    <ThemeContext.Provider
      value={{
        colors: isDark ? dark : light,
        isDark,
        toggleTheme,
        fontScale: FONT_SCALE[fontSizeKey],
        fontSizeKey,
        setFontSize,
      }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  return useContext(ThemeContext);
}
