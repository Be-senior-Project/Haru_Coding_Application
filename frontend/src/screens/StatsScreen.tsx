import React, {useCallback, useMemo, useState} from 'react';
import {View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTheme, type Colors} from '../theme/ThemeContext';
import {useFocusEffect} from '@react-navigation/native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {statsApi, type BreakdownStat, type StatsData, type StatsPeriod} from '../api/statsApi';
import {
  categoryLabel, TYPE_LABEL, DIFFICULTY_LABEL, difficultyColor, difficultySoft, type ProblemType,
} from '../types/problem';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import ProgressRing from '../components/ProgressRing';

const WEEK_DAYS = ['월', '화', '수', '목', '금', '토', '일'];
const TABS = ['전체', '주간', '월간', '연간'] as const;
const TAB_TO_PERIOD: Record<(typeof TABS)[number], StatsPeriod> = {
  전체: 'ALL',
  주간: 'WEEK',
  월간: 'MONTH',
  연간: 'YEAR',
};

// 탭별로 "푼 문제 수" 카드에 붙일 설명
function solvedLabel(tab: string): string {
  switch (tab) {
    case '주간': return '이번 주 푼 문제';
    case '월간': return '이번 달 푼 문제';
    case '연간': return '올해 푼 문제';
    default: return '전체 푼 문제';
  }
}

const md = (d: Date) => `${d.getMonth() + 1}.${d.getDate()}`;

// 월간 달력(일요일 시작)과 연간 히트맵(월요일 시작)의 요일 순서는 피그마 디자인을 따른다
const CAL_DAYS = ['일', '월', '화', '수', '목', '금', '토'];
const HEAT_DAYS = ['월', '화', '수', '목', '금', '토', '일'];
const HEAT_OPACITY = [0.3, 0.5, 0.75, 1]; // 히트맵 1~4단계(1·2·3·4문제 이상) 진하기. 0단계는 border 색
const HEAT_LABEL_W = 14;  // 히트맵 왼쪽 요일 칸 너비
const HEAT_GAP = 2;       // 히트맵 칸 사이 간격
const DAY_MS = 24 * 60 * 60 * 1000;

// 서버는 날짜별 풀이 수를 한국 시간(yyyy-MM-dd)으로 준다. 기기 시간대와 상관없이 같은 날짜로 맞추려고
// 한국 시간 기준 오늘을 구하고, 날짜 계산은 UTC 값으로 한다(일광 절약 시간에 날짜가 밀리지 않음).
function kstToday(): {y: number; m: number} {
  const d = new Date(Date.now() + 9 * 60 * 60 * 1000);
  return {y: d.getUTCFullYear(), m: d.getUTCMonth()};
}
const utcKey = (t: number) => new Date(t).toISOString().slice(0, 10);

// 선택한 탭에 맞는 카드 제목과 기간 표기
function periodInfo(tab: string): {title: string; range: string} {
  const now = new Date();
  switch (tab) {
    case '주간': {
      const mon = new Date(now);
      mon.setDate(now.getDate() - ((now.getDay() + 6) % 7)); // 월=0 기준
      const sun = new Date(mon);
      sun.setDate(mon.getDate() + 6);
      return {title: '이번 주 학습 현황', range: `${md(mon)} - ${md(sun)}`};
    }
    case '월간': {
      const first = new Date(now.getFullYear(), now.getMonth(), 1);
      const last = new Date(now.getFullYear(), now.getMonth() + 1, 0);
      return {title: '이번 달 학습 현황', range: `${md(first)} - ${md(last)}`};
    }
    case '연간':
      return {title: '올해 학습 현황', range: `${now.getFullYear()}.1.1 - 12.31`};
    default:
      return {title: '전체 학습 현황', range: '전체 기간'};
  }
}

export default function StatsScreen() {
  const insets = useSafeAreaInsets();
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);

  const [stats, setStats] = useState<StatsData | null>(null);
  const [loading, setLoading] = useState(false);
  const [hasToken, setHasToken] = useState(false);
  const [tab, setTab] = useState<(typeof TABS)[number]>('전체');

  // 화면에 들어올 때와 탭을 바꿀 때 모두 "지금 선택된 탭" 기준으로 불러온다.
  // (예전엔 처음 탭 값을 기억한 채 다시 불러와, 탭은 '주간'인데 데이터는 '전체'로 보이는 경우가 있었다)
  useFocusEffect(
    useCallback(() => {
      let alive = true;
      (async () => {
        const token = await AsyncStorage.getItem('accessToken');
        if (!alive) {return;}
        setHasToken(!!token);
        if (!token) {return;}
        setLoading(true);
        try {
          const s = await statsApi.getMyStats(TAB_TO_PERIOD[tab]);
          if (alive) {setStats(s);}
        } catch (e) {
          console.error('통계 로드 실패', e);
        } finally {
          if (alive) {setLoading(false);}
        }
      })();
      return () => {
        alive = false;
      };
    }, [tab]),
  );

  const handleTabPress = (t: (typeof TABS)[number]) => setTab(t);

  // weeklyActivity → 항상 길이 7의 "유효한 숫자"로 정규화 (월~일)
  const week = useMemo(() => {
    const w = stats?.weeklyActivity ?? [];
    return Array.from({length: 7}, (_, i) => Number(w[i]) || 0);
  }, [stats]);
  const maxBar = Math.max(...week, 1);
  const todayIdx = (new Date().getDay() + 6) % 7; // 월=0 기준

  // 약점 영역: 서버가 기준(3문제 이상 풀고 정답률 70% 미만)으로 고른 영역. 없으면 안내를 띄우지 않는다.
  // (예전엔 정답률이 가장 낮은 영역을 무조건 약점으로 보여 줘서, 영역이 하나면 100%여도 약점으로 나왔다)
  const weakName = stats?.weakCategory ? categoryLabel(stats.weakCategory) : null;

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  if (!hasToken) {
    return (
      <View style={[styles.container, styles.center, styles.lockPad]}>
        <MaterialIcons name="lock" size={32} color={colors.subText} />
        <Text style={styles.emptyText}>로그인하면 학습 통계를 볼 수 있어요</Text>
      </View>
    );
  }

  const accuracy = stats?.accuracyRate ?? 0;
  const period = periodInfo(tab);

  return (
    // 상단 여백(insets.top)을 스크롤 영역 밖에 둬야 스크롤한 내용이 상태 표시줄 밑으로 들어가지 않는다
    <View style={[styles.container, {paddingTop: insets.top}]}>
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={[styles.content, styles.contentTop]}>

      {/* 헤더 */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>학습 통계</Text>
      </View>

      {/* 기간 탭 */}
      <View style={styles.tabRow}>
        {TABS.map(t => (
          <TouchableOpacity key={t} style={styles.tab} onPress={() => handleTabPress(t)}>
            <Text style={[styles.tabText, tab === t && styles.tabTextActive]}>{t}</Text>
            {tab === t && <View style={styles.tabUnderline} />}
          </TouchableOpacity>
        ))}
      </View>

      {/* 이번 주 학습 현황 */}
      <View style={styles.card}>
        <View style={styles.cardTitleRow}>
          <Text style={styles.cardLabel}>{period.title}</Text>
          <Text style={styles.cardDate}>{period.range}</Text>
        </View>
        <View style={[styles.weekTop, tab === '전체' && styles.weekTopOnly]}>
          <View style={styles.weekTopLeft}>
            <Text style={styles.cardSub}>{solvedLabel(tab)}</Text>
            <View style={styles.weekValueRow}>
              <Text style={styles.weekValue}>{stats?.totalSolved ?? 0}</Text>
              <Text style={styles.weekUnit}> 문제</Text>
            </View>
          </View>
          {/* 정답률 링 — 예전엔 값과 상관없이 늘 3/4이 칠해진 장식이었다 */}
          <ProgressRing
            size={84}
            thickness={6}
            progress={accuracy / 100}
            color={colors.primary}
            trackColor={colors.border}>
            <Text style={styles.ringValue}>{accuracy}%</Text>
            <Text style={styles.ringLabel}>정답률</Text>
          </ProgressRing>
        </View>

        {/* 활동 기록: 주간은 요일별 막대, 월간은 달력, 연간은 히트맵(피그마 디자인).
            전체 탭은 기간이 정해져 있지 않아 그래프를 두지 않고, 아래에 유형별·난이도별 정답률 카드를 보여 준다. */}
        {tab === '주간' && (
          <>
            {/* 바 차트는 백엔드가 주는 weeklyActivity(이번 주 월~일) 기준 */}
            <Text style={styles.chartCaption}>이번 주 활동</Text>
            <View style={styles.barChart}>
              {week.map((h, i) => (
                <View key={i} style={styles.barWrapper}>
                  <Text style={styles.barTop}>{h > 0 ? h : ''}</Text>
                  <View style={[
                    styles.bar,
                    {
                      height: Math.max((h / maxBar) * 70, 4),
                      backgroundColor: i === todayIdx ? colors.primary : colors.primarySoft,
                    },
                  ]} />
                  <Text style={styles.barLabel}>{WEEK_DAYS[i]}</Text>
                </View>
              ))}
            </View>
          </>
        )}
        {tab === '월간' && (
          <>
            <Text style={styles.chartCaption}>이번 달 활동</Text>
            <MonthCalendar daily={stats?.dailyActivity ?? {}} styles={styles} />
          </>
        )}
        {tab === '연간' && (
          <>
            <Text style={styles.chartCaption}>올해 활동</Text>
            <YearHeatmap daily={stats?.dailyActivity ?? {}} styles={styles} colors={colors} />
          </>
        )}
      </View>

      {/* 핵심 지표 3종 (실제 값) */}
      <View style={styles.metricRow}>
        <Metric icon="article" color={colors.primary} value={`${stats?.totalSolved ?? 0}`} unit="개" label="푼 문제 수" colors={colors} fs={fontScale} />
        <Metric icon="check-circle" color={colors.success} value={`${accuracy}`} unit="%" label="정답률" colors={colors} fs={fontScale} />
        <Metric icon="local-fire-department" color={colors.streak} value={`${stats?.currentStreak ?? 0}`} unit="일" label="연속 학습" colors={colors} fs={fontScale} />
      </View>

      {/* 전체 탭: 활동 그래프 대신 유형별·난이도별 정답률(문제별 첫 제출 기준) */}
      {tab === '전체' && (stats?.typeStats?.length ?? 0) > 0 && (
        <>
          <Text style={styles.sectionTitle}>유형별 정답률</Text>
          <View style={[styles.card, styles.breakdownCard]}>
            {stats!.typeStats!.map((s, i) => (
              <BreakdownRow key={s.key} stat={s} first={i === 0} styles={styles}
                label={<Text style={styles.topicLabel}>{TYPE_LABEL[s.key as ProblemType] ?? s.key}</Text>} />
            ))}
          </View>
        </>
      )}
      {tab === '전체' && (stats?.difficultyStats?.length ?? 0) > 0 && (
        <>
          <Text style={styles.sectionTitle}>난이도별 정답률</Text>
          <View style={[styles.card, styles.breakdownCard]}>
            {stats!.difficultyStats!.map((s, i) => {
              const d = Number(s.key);
              return (
                <BreakdownRow key={s.key} stat={s} first={i === 0} styles={styles}
                  label={
                    <View style={[styles.diffBadge, {backgroundColor: difficultySoft(d, colors)}]}>
                      <Text style={[styles.diffBadgeText, {color: difficultyColor(d, colors)}]}>
                        {DIFFICULTY_LABEL[d] ?? s.key}
                      </Text>
                    </View>
                  } />
              );
            })}
          </View>
        </>
      )}

      {/* 실력 분석 (주제별 정답률 + 약점 팁) */}
      <Text style={styles.sectionTitle}>실력 분석</Text>
      {(stats?.categoryStats?.length ?? 0) === 0 ? (
        <View style={styles.emptyCard}>
          <Text style={styles.emptyText}>아직 푼 문제가 없어요</Text>
        </View>
      ) : (
        <View style={styles.card}>
          {stats!.categoryStats.map((cat, i) => {
            const acc = Math.min(Number(cat.accuracyRate) || 0, 100);
            return (
              <View key={cat.topicId} style={[styles.topicRow, i > 0 && styles.topicDivider]}>
                <View style={styles.topicLabelRow}>
                  <Text style={styles.topicLabel}>{categoryLabel(cat.topicName)}</Text>
                  <Text style={styles.topicPct}>{acc.toFixed(0)}%</Text>
                </View>
                <View style={styles.topicBarBg}>
                  <View style={[styles.topicBarFill, {width: `${acc}%`}]} />
                </View>
                <Text style={styles.topicSub}>{Number(cat.correctCount) || 0}/{Number(cat.totalSolved) || 0} 정답</Text>
              </View>
            );
          })}

          {weakName && (
            <View style={styles.tipBox}>
              <MaterialIcons name="lightbulb" size={18} color={colors.warning} />
              <Text style={styles.tipText}>
                <Text style={styles.tipStrong}>{weakName}</Text> 영역이 약점이에요! 해당 영역 문제를 더 풀어보는 걸 추천해요.
              </Text>
            </View>
          )}
        </View>
      )}

      <View style={styles.bottomSpacer} />
    </ScrollView>
    </View>
  );
}

function Metric({icon, color, value, unit, label, colors, fs}: {
  icon: string; color: string; value: string; unit: string; label: string; colors: Colors; fs: number;
}) {
  const styles = makeStyles(colors, fs);
  return (
    <View style={styles.metricCard}>
      <MaterialIcons name={icon} size={20} color={color} />
      <View style={styles.metricValueRow}>
        <Text style={styles.metricValue}>{value}</Text>
        <Text style={styles.metricUnit}>{unit}</Text>
      </View>
      <Text style={styles.metricLabel}>{label}</Text>
    </View>
  );
}

type Styles = ReturnType<typeof makeStyles>;

// 유형별·난이도별 정답률 한 줄. 아직 안 푼 항목은 0%(전부 틀림)로 보이지 않게 "-"와 안내 문구를 쓴다.
function BreakdownRow({stat, label, first, styles}: {
  stat: BreakdownStat; label: React.ReactNode; first: boolean; styles: Styles;
}) {
  const solved = Number(stat.totalSolved) || 0;
  const acc = Math.min(Number(stat.accuracyRate) || 0, 100);
  return (
    <View style={[styles.topicRow, !first && styles.topicDivider]}>
      <View style={styles.topicLabelRow}>
        {label}
        <Text style={solved > 0 ? styles.topicPct : styles.topicPctNone}>
          {solved > 0 ? `${acc.toFixed(0)}%` : '-'}
        </Text>
      </View>
      <View style={styles.topicBarBg}>
        <View style={[styles.topicBarFill, {width: `${acc}%`}]} />
      </View>
      <Text style={styles.topicSub}>
        {solved > 0 ? `${Number(stat.correctCount) || 0}/${solved} 정답` : '아직 안 풀었어요'}
      </Text>
    </View>
  );
}

/**
 * 월간 탭 달력(피그마 STATS(month)). 이번 달 날짜마다 처음 푼 문제 수를 보여 주고,
 * 같은 줄에서 이어서 푼 날은 하나의 띠로 이어 양 끝만 둥글게 그린다.
 */
function MonthCalendar({daily, styles}: {daily: Record<string, number>; styles: Styles}) {
  const {y, m} = kstToday();
  const lead = new Date(Date.UTC(y, m, 1)).getUTCDay();          // 1일 앞 빈칸 수(일요일 시작)
  const lastDate = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
  const cells: (number | null)[] = [
    ...Array<null>(lead).fill(null),
    ...Array.from({length: lastDate}, (_, i) => i + 1),
  ];
  while (cells.length % 7) {
    cells.push(null);
  }
  const weeks = Array.from({length: cells.length / 7}, (_, w) => cells.slice(w * 7, w * 7 + 7));
  const count = (d: number | null) => (d === null ? 0 : daily[utcKey(Date.UTC(y, m, d))] ?? 0);

  return (
    <View>
      <Text style={styles.calTitle}>{m + 1}월 {y}</Text>
      <View style={styles.calRow}>
        {CAL_DAYS.map(d => (
          <Text key={d} style={styles.calDow}>{d}</Text>
        ))}
      </View>
      {weeks.map((week, wi) => (
        <View key={wi} style={styles.calRow}>
          {week.map((d, i) => {
            const n = count(d);
            const on = n > 0;
            return (
              <View key={i} style={styles.calCell}>
                {d !== null && (
                  <View style={[
                    styles.calDay,
                    on && styles.calDayOn,
                    on && i > 0 && count(week[i - 1]) > 0 && styles.calJoinLeft,
                    on && i < 6 && count(week[i + 1]) > 0 && styles.calJoinRight,
                  ]}>
                    <Text style={on ? styles.calNumOn : styles.calNum}>{d}</Text>
                    {on && <Text style={styles.calCount}>{n}문제</Text>}
                  </View>
                )}
              </View>
            );
          })}
        </View>
      ))}
    </View>
  );
}

// 상반기(1~6월) 또는 하반기(7~12월)의 주 배치. 한 열이 한 주(월요일 시작)이다.
function halfYear(y: number, startMonth: number) {
  const first = Date.UTC(y, startMonth, 1);
  const lead = (new Date(first).getUTCDay() + 6) % 7;            // 첫날 앞 빈칸 수(월요일 시작)
  const days = (Date.UTC(y, startMonth + 6, 1) - first) / DAY_MS;
  return {
    start: startMonth,
    first,
    lead,
    days,
    cols: Math.ceil((lead + days) / 7),
    // 각 달 1일이 들어 있는 열 = 월 이름을 놓을 자리
    monthCols: Array.from({length: 6}, (_, k) =>
      Math.floor((lead + (Date.UTC(y, startMonth + k, 1) - first) / DAY_MS) / 7)),
  };
}

/**
 * 연간 탭 히트맵(피그마 STATS(year)). 상반기·하반기를 위아래로 나눠 날짜마다 처음 푼 문제 수를 색 진하기로 보여 준다.
 * 칸 크기는 카드 너비를 재서 맞춘다.
 */
function YearHeatmap({daily, styles, colors}: {daily: Record<string, number>; styles: Styles; colors: Colors}) {
  const [width, setWidth] = useState(0);
  const {y} = kstToday();
  const halves = [halfYear(y, 0), halfYear(y, 6)];
  const cols = Math.max(...halves.map(h => h.cols));
  const cell = width > 0 ? (width - HEAT_LABEL_W - cols * HEAT_GAP) / cols : 0;
  // 진하기는 그날 푼 문제 수 그대로 1·2·3·4문제 이상(하루 목표 최소값 4)으로 나눈다.
  // 가장 많이 푼 날을 기준으로 나누면 하루만 많이 풀어도 나머지 날이 모두 옅어진다.
  const level = (n: number) => Math.max(0, Math.min(4, n));
  // 0단계는 border 색, 1~4단계는 primary 색의 진하기만 달리한다(새 색을 만들지 않음)
  const tone = (lv: number) =>
    lv === 0 ? {backgroundColor: colors.border} : {backgroundColor: colors.primary, opacity: HEAT_OPACITY[lv - 1]};
  const size = {width: cell, height: cell};

  return (
    <View onLayout={e => setWidth(e.nativeEvent.layout.width)}>
      <View style={styles.legendRow}>
        <Text style={styles.legendText}>적음</Text>
        {[0, 1, 2, 3, 4].map(lv => (
          <View key={lv} style={[styles.legendCell, tone(lv)]} />
        ))}
        <Text style={styles.legendText}>많음</Text>
      </View>
      {cell > 0 && halves.map(h => (
        <View key={h.start} style={styles.heatBlock}>
          <View style={styles.heatMonths}>
            {h.monthCols.map((col, k) => (
              <Text key={k} style={[styles.heatMonth, {left: HEAT_LABEL_W + HEAT_GAP + col * (cell + HEAT_GAP)}]}>
                {h.start + k + 1}월
              </Text>
            ))}
          </View>
          <View style={styles.heatBody}>
            <View style={styles.heatLabelCol}>
              {HEAT_DAYS.map(d => (
                <Text key={d} style={[styles.heatDow, {height: cell}]}>{d}</Text>
              ))}
            </View>
            {Array.from({length: cols}, (_, c) => (
              <View key={c} style={styles.heatCol}>
                {HEAT_DAYS.map((_day, r) => {
                  const idx = c * 7 + r - h.lead;
                  // 이 반기에 속하지 않는 칸(첫 주의 앞, 마지막 주의 뒤)은 비워 둔다
                  if (idx < 0 || idx >= h.days) {
                    return <View key={r} style={size} />;
                  }
                  const n = daily[utcKey(h.first + idx * DAY_MS)] ?? 0;
                  return <View key={r} style={[styles.heatCell, size, tone(level(n))]} />;
                })}
              </View>
            ))}
          </View>
        </View>
      ))}
    </View>
  );
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    scroll: {flex: 1},
    center: {justifyContent: 'center', alignItems: 'center', gap: 10},
    lockPad: {padding: 40},
    content: {padding: 20, paddingBottom: 32},
    contentTop: {paddingTop: 8},
    bottomSpacer: {height: 12},

    header: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14},
    headerTitle: {fontSize: 19 * fs, fontWeight: '800', color: c.text},

    // 탭
    tabRow: {flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: c.border, marginBottom: 18},
    tab: {flex: 1, alignItems: 'center', paddingVertical: 10},
    tabText: {fontSize: 14 * fs, color: c.subText, fontWeight: '600'},
    tabTextActive: {color: c.primary, fontWeight: '800'},
    tabUnderline: {position: 'absolute', bottom: -1, height: 2, width: '60%', backgroundColor: c.primary, borderRadius: 1},

    card: {
      backgroundColor: c.card, borderRadius: 20, padding: 18, marginBottom: 16,
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 10, shadowOffset: {width: 0, height: 4}, elevation: 2,
    },
    cardTitleRow: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10},
    cardLabel: {fontSize: 14 * fs, fontWeight: '700', color: c.text},
    cardDate: {fontSize: 12 * fs, color: c.subText},
    cardSub: {fontSize: 12 * fs, color: c.subText, marginBottom: 2},

    weekTop: {flexDirection: 'row', alignItems: 'center', marginBottom: 16},
    weekTopOnly: {marginBottom: 0}, // 전체 탭은 아래 그래프가 없어 카드 하단 여백을 없앤다
    weekTopLeft: {flex: 1},
    weekValueRow: {flexDirection: 'row', alignItems: 'baseline'},
    weekValue: {fontSize: 30 * fs, fontWeight: '900', color: c.primary},
    weekUnit: {fontSize: 15 * fs, fontWeight: '600', color: c.subText},

    // 링
    ringValue: {fontSize: 17 * fs, fontWeight: '800', color: c.text},
    ringLabel: {fontSize: 10 * fs, color: c.subText, marginTop: 1},

    // 바 차트
    chartCaption: {fontSize: 11 * fs, color: c.subText, marginBottom: 6},
    barChart: {flexDirection: 'row', alignItems: 'flex-end', gap: 6, height: 100},
    barWrapper: {flex: 1, alignItems: 'center', justifyContent: 'flex-end', gap: 4},
    barTop: {fontSize: 9 * fs, color: c.subText, height: 12},
    bar: {width: '70%', borderRadius: 5},
    barLabel: {fontSize: 10 * fs, color: c.subText},

    // 월간 달력
    calTitle: {fontSize: 15 * fs, fontWeight: '800', color: c.text, textAlign: 'center', marginTop: 4, marginBottom: 10},
    calRow: {flexDirection: 'row', marginBottom: 6},
    calDow: {flex: 1, textAlign: 'center', fontSize: 13 * fs, fontWeight: '700', color: c.subText},
    calCell: {flex: 1},
    calDay: {height: 40 * fs, alignItems: 'center', justifyContent: 'center', borderRadius: 20 * fs},
    calDayOn: {backgroundColor: c.primary},
    // 칸 너비가 소수점이라 생기는 칸 사이 가는 틈을 덮으려고 왼쪽으로 1만큼 겹친다
    calJoinLeft: {borderTopLeftRadius: 0, borderBottomLeftRadius: 0, marginLeft: -1},
    calJoinRight: {borderTopRightRadius: 0, borderBottomRightRadius: 0},
    calNum: {fontSize: 14 * fs, color: c.subText},
    calNumOn: {fontSize: 14 * fs, fontWeight: '800', color: c.onPrimary},
    calCount: {fontSize: 8 * fs, color: c.onPrimary},

    // 연간 히트맵
    legendRow: {flexDirection: 'row', alignItems: 'center', gap: 4, marginBottom: 4},
    legendText: {fontSize: 10 * fs, color: c.subText},
    legendCell: {width: 9, height: 9, borderRadius: 2},
    heatBlock: {marginTop: 10},
    heatMonths: {height: 13 * fs, marginBottom: 3},
    heatMonth: {position: 'absolute', top: 0, fontSize: 9 * fs, color: c.subText},
    heatBody: {flexDirection: 'row', gap: HEAT_GAP},
    heatLabelCol: {width: HEAT_LABEL_W, gap: HEAT_GAP},
    heatCol: {gap: HEAT_GAP},
    heatDow: {fontSize: 8 * fs, color: c.subText, textAlignVertical: 'center', includeFontPadding: false},
    heatCell: {borderRadius: 2},

    // 지표 3종
    metricRow: {flexDirection: 'row', gap: 10, marginBottom: 16},
    metricCard: {
      flex: 1, backgroundColor: c.card, borderRadius: 16, paddingVertical: 16, alignItems: 'center', gap: 4,
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 8, shadowOffset: {width: 0, height: 3}, elevation: 2,
    },
    metricValueRow: {flexDirection: 'row', alignItems: 'baseline'},
    metricValue: {fontSize: 22 * fs, fontWeight: '900', color: c.primary},
    metricUnit: {fontSize: 13 * fs, fontWeight: '700', color: c.subText},
    metricLabel: {fontSize: 11 * fs, color: c.subText},

    sectionTitle: {fontSize: 16 * fs, fontWeight: '800', color: c.text, marginBottom: 12},

    // 주제별
    topicRow: {paddingVertical: 12},
    topicDivider: {borderTopWidth: 1, borderTopColor: c.border},
    topicLabelRow: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6},
    topicLabel: {fontSize: 14 * fs, fontWeight: '700', color: c.text},
    topicPct: {fontSize: 13 * fs, fontWeight: '700', color: c.primary},
    topicPctNone: {fontSize: 13 * fs, fontWeight: '700', color: c.subText},

    // 유형별·난이도별 정답률 카드
    breakdownCard: {paddingVertical: 6},
    diffBadge: {paddingHorizontal: 8, paddingVertical: 2, borderRadius: 6},
    diffBadgeText: {fontSize: 12 * fs, fontWeight: '700'},
    topicBarBg: {height: 7, backgroundColor: c.border, borderRadius: 4, overflow: 'hidden', marginBottom: 4},
    topicBarFill: {height: '100%', backgroundColor: c.primary, borderRadius: 4},
    topicSub: {fontSize: 11 * fs, color: c.subText},

    // 약점 팁
    tipBox: {
      flexDirection: 'row', gap: 8, alignItems: 'flex-start', marginTop: 14,
      backgroundColor: c.primarySoft, borderRadius: 12, padding: 12,
    },
    tipText: {flex: 1, fontSize: 12 * fs, color: c.subText, lineHeight: 18 * fs},
    tipStrong: {fontWeight: '800', color: c.text},

    emptyCard: {backgroundColor: c.card, borderRadius: 16, padding: 24, alignItems: 'center', marginBottom: 16},
    emptyText: {fontSize: 14 * fs, color: c.subText, textAlign: 'center'},
  });
}
