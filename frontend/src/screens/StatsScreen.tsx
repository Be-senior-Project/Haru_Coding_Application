import React, {useCallback, useMemo, useState} from 'react';
import {View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity, Alert} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTheme, type Colors} from '../theme/ThemeContext';
import {useFocusEffect} from '@react-navigation/native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {statsApi, type StatsData} from '../api/statsApi';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

const WEEK_DAYS = ['월', '화', '수', '목', '금', '토', '일'];
const TABS = ['전체', '주간', '월간', '연간'];

// solvedAt(ISO) → "M/D HH:mm"
function formatDate(iso: string): string {
  const d = new Date(iso);
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
}

const md = (d: Date) => `${d.getMonth() + 1}.${d.getDate()}`;

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
  const [tab, setTab] = useState('전체');

  useFocusEffect(
    useCallback(() => {
      loadStats();
    }, []),
  );

  const loadStats = async () => {
    const token = await AsyncStorage.getItem('accessToken');
    setHasToken(!!token);
    if (!token) {return;}
    setLoading(true);
    try {
      setStats(await statsApi.getMyStats());
    } catch (e) {
      console.error('통계 로드 실패', e);
    } finally {
      setLoading(false);
    }
  };

  // weeklyActivity → 항상 길이 7의 "유효한 숫자"로 정규화 (월~일)
  const week = useMemo(() => {
    const w = stats?.weeklyActivity ?? [];
    return Array.from({length: 7}, (_, i) => Number(w[i]) || 0);
  }, [stats]);
  const maxBar = Math.max(...week, 1);
  const weekTotal = week.reduce((a, b) => a + b, 0);
  const todayIdx = (new Date().getDay() + 6) % 7; // 월=0 기준

  // 약점 영역: 푼 적 있는 주제 중 정답률 최저 (실제 데이터)
  const weakest = useMemo(() => {
    const cs = (stats?.categoryStats ?? []).filter(c => (Number(c.totalSolved) || 0) > 0);
    if (cs.length === 0) {return null;}
    return cs.reduce((min, c) => (Number(c.accuracyRate) < Number(min.accuracyRate) ? c : min));
  }, [stats]);

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
  const isAllTab = tab === '전체';

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingTop: insets.top + 8}]}>

      {/* 헤더 */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>학습 통계</Text>
      </View>

      {/* 기간 탭 */}
      <View style={styles.tabRow}>
        {TABS.map(t => (
          <TouchableOpacity key={t} style={styles.tab} onPress={() => setTab(t)}>
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
        <View style={styles.weekTop}>
          <View style={styles.weekTopLeft}>
            <Text style={styles.cardSub}>{isAllTab ? '전체 푼 문제' : '최근 7일 푼 문제'}</Text>
            <View style={styles.weekValueRow}>
              <Text style={styles.weekValue}>{isAllTab ? (stats?.totalSolved ?? 0) : weekTotal}</Text>
              <Text style={styles.weekUnit}> 문제</Text>
            </View>
          </View>
          {/* 정답률 링 (실제 정답률) */}
          <View style={styles.ringWrap}>
            <View style={styles.ring} />
            <View style={styles.ringInner}>
              <Text style={styles.ringValue}>{accuracy}%</Text>
              <Text style={styles.ringLabel}>정답률</Text>
            </View>
          </View>
        </View>

        {/* 바 차트는 백엔드가 주는 weeklyActivity(월~일) 기준이라 탭과 무관하게 최근 7일 */}
        <Text style={styles.chartCaption}>최근 7일 활동</Text>
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
      </View>

      {/* 핵심 지표 3종 (실제 값) */}
      <View style={styles.metricRow}>
        <Metric icon="article" color={colors.primary} value={`${stats?.totalSolved ?? 0}`} unit="개" label="푼 문제 수" colors={colors} fs={fontScale} />
        <Metric icon="check-circle" color={colors.success} value={`${accuracy}`} unit="%" label="정답률" colors={colors} fs={fontScale} />
        <Metric icon="local-fire-department" color={colors.streak} value={`${stats?.currentStreak ?? 0}`} unit="일" label="연속 학습" colors={colors} fs={fontScale} />
      </View>

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
                  <Text style={styles.topicLabel}>{cat.topicName}</Text>
                  <Text style={styles.topicPct}>{acc.toFixed(0)}%</Text>
                </View>
                <View style={styles.topicBarBg}>
                  <View style={[styles.topicBarFill, {width: `${acc}%`}]} />
                </View>
                <Text style={styles.topicSub}>{Number(cat.correctCount) || 0}/{Number(cat.totalSolved) || 0} 정답</Text>
              </View>
            );
          })}

          {weakest && (
            <View style={styles.tipBox}>
              <MaterialIcons name="lightbulb" size={18} color={colors.warning} />
              <Text style={styles.tipText}>
                <Text style={styles.tipStrong}>{weakest.topicName}</Text> 영역이 약점이에요! 해당 영역 문제를 더 풀어보는 걸 추천해요.
              </Text>
            </View>
          )}
        </View>
      )}

      {/* 최근 푼 문제 (실제 recentRecords) */}
      {stats && stats.recentRecords.length > 0 && (
        <>
          <View style={styles.recentHead}>
            <Text style={styles.sectionTitle}>최근 푼 문제</Text>
            <TouchableOpacity onPress={() => Alert.alert('더보기', '곧 추가될 기능이에요.')} hitSlop={6}>
              <Text style={styles.moreText}>더보기</Text>
            </TouchableOpacity>
          </View>
          <View style={styles.card}>
            {stats.recentRecords.map((r, i) => (
              <View key={i} style={[styles.recentItem, i > 0 && styles.topicDivider]}>
                <MaterialIcons
                  name={r.isCorrect ? 'check-circle' : 'cancel'}
                  size={20}
                  color={r.isCorrect ? colors.success : colors.danger}
                />
                <View style={styles.recentInfo}>
                  <Text style={styles.recentTitle} numberOfLines={1}>{r.problemTitle}</Text>
                  <Text style={styles.recentMeta}>{r.topic} · {formatDate(r.solvedAt)}</Text>
                </View>
                <Text style={[styles.recentResult, {color: r.isCorrect ? colors.success : colors.danger}]}>
                  {r.isCorrect ? '정답' : '오답'}
                </Text>
              </View>
            ))}
          </View>
        </>
      )}

      <View style={styles.bottomSpacer} />
    </ScrollView>
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

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    center: {justifyContent: 'center', alignItems: 'center', gap: 10},
    lockPad: {padding: 40},
    content: {padding: 20, paddingBottom: 32},
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
    weekTopLeft: {flex: 1},
    weekValueRow: {flexDirection: 'row', alignItems: 'baseline'},
    weekValue: {fontSize: 30 * fs, fontWeight: '900', color: c.primary},
    weekUnit: {fontSize: 15 * fs, fontWeight: '600', color: c.subText},

    // 링
    ringWrap: {width: 84, height: 84, alignItems: 'center', justifyContent: 'center'},
    ring: {
      position: 'absolute', width: 84, height: 84, borderRadius: 42, borderWidth: 6,
      borderColor: c.primary, borderBottomColor: c.border, transform: [{rotate: '-45deg'}],
    },
    ringInner: {alignItems: 'center'},
    ringValue: {fontSize: 17 * fs, fontWeight: '800', color: c.text},
    ringLabel: {fontSize: 10 * fs, color: c.subText, marginTop: 1},

    // 바 차트
    chartCaption: {fontSize: 11 * fs, color: c.subText, marginBottom: 6},
    barChart: {flexDirection: 'row', alignItems: 'flex-end', gap: 6, height: 100},
    barWrapper: {flex: 1, alignItems: 'center', justifyContent: 'flex-end', gap: 4},
    barTop: {fontSize: 9 * fs, color: c.subText, height: 12},
    bar: {width: '70%', borderRadius: 5},
    barLabel: {fontSize: 10 * fs, color: c.subText},

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
    topicLabelRow: {flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6},
    topicLabel: {fontSize: 14 * fs, fontWeight: '700', color: c.text},
    topicPct: {fontSize: 13 * fs, fontWeight: '700', color: c.primary},
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

    // 최근
    recentHead: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 4},
    moreText: {fontSize: 13 * fs, color: c.subText, marginBottom: 12},
    recentItem: {flexDirection: 'row', alignItems: 'center', paddingVertical: 12, gap: 12},
    recentInfo: {flex: 1},
    recentTitle: {fontSize: 14 * fs, fontWeight: '700', color: c.text},
    recentMeta: {fontSize: 12 * fs, color: c.subText, marginTop: 2},
    recentResult: {fontSize: 13 * fs, fontWeight: '800'},

    emptyCard: {backgroundColor: c.card, borderRadius: 16, padding: 24, alignItems: 'center', marginBottom: 16},
    emptyText: {fontSize: 14 * fs, color: c.subText, textAlign: 'center'},
  });
}
