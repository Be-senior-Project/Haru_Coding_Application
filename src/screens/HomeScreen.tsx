import React, {useCallback, useMemo, useState} from 'react';
import {View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert, ActivityIndicator} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {useNavigation, useFocusEffect} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTheme, type Colors} from '../theme/ThemeContext';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import MaterialCommunityIcons from 'react-native-vector-icons/MaterialCommunityIcons';
import {recommendationApi, type RecommendationResponse} from '../api/recommendationApi';
import {problemApi} from '../api/problemApi';
import {statsApi, type StatsData} from '../api/statsApi';
import {userApi, type UserProfile} from '../api/userApi';

// 추천 학습 주제 (데모) — 각 카드 색상/아이콘/문제 수
const TOPICS: {label: string; count: number; icon: string; tint: string; accent: string; progress: number}[] = [
  {label: '자료구조', count: 20, icon: 'format-list-bulleted', tint: '#E6F8F0', accent: '#26C281', progress: 0.4},
  {label: '그리디', count: 18, icon: 'bolt', tint: '#E7F1FE', accent: '#3B82F6', progress: 0.3},
  {label: '동적 계획법', count: 25, icon: 'memory', tint: '#EEEBFF', accent: '#6C5CE7', progress: 0.55},
  {label: '이진 탐색', count: 15, icon: 'search', tint: '#FFF1E6', accent: '#FB8C00', progress: 0.2},
];

// 리그 티어 영문(백엔드) → 한글 표시 라벨
const TIER_KO: Record<string, string> = {
  BRONZE: '브론즈',
  SILVER: '실버',
  GOLD: '골드',
  PLATINUM: '플래티넘',
  DIAMOND: '다이아',
};

export default function HomeScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const [streak, setStreak] = useState(0);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [_rec, setRec] = useState<RecommendationResponse | null>(null);
  const [stats, setStats] = useState<StatsData | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [todayCount, setTodayCount] = useState(0); // 오늘 푼 문제 수
  const insets = useSafeAreaInsets();
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);

  // 화면에 들어올 때마다 갱신 (문제 풀고 돌아오면 오늘 푼 문제 수 반영)
  useFocusEffect(
    useCallback(() => {
      loadData();
    }, []),
  );

  const loadData = async () => {
    const saved = await AsyncStorage.getItem('streak');
    const token = await AsyncStorage.getItem('accessToken');
    const today = new Date().toISOString().split('T')[0];
    const tc = await AsyncStorage.getItem(`solvedCount_${today}`);
    setTodayCount(tc ? parseInt(tc, 10) : 0);
    if (saved) {setStreak(parseInt(saved, 10));}
    setIsLoggedIn(!!token);
    if (token) {
      try {
        setRec(await recommendationApi.get(5));
      } catch {}
      try {
        setStats(await statsApi.getMyStats());
      } catch {}
      try {
        setProfile(await userApi.getMe());
      } catch {}
    }
  };

  const [starting, setStarting] = useState(false);

  // 세트 시작: 백엔드가 안 푼 DB 문제 우선, 없으면 즉석 생성해서 줌
  const handleStartSet = async () => {
    if (starting) {return;}
    const token = await AsyncStorage.getItem('accessToken');
    if (!token) {
      navigation.navigate('Login');
      return;
    }
    setStarting(true);
    try {
      const problems = await problemApi.startSet(4);
      if (!problems || problems.length === 0) {
        Alert.alert('준비 중', '문제를 준비하지 못했어요. 잠시 후 다시 시도해주세요.');
        return;
      }
      navigation.navigate('ProblemSolve', {problems});
    } catch (e: any) {
      const status = e?.status;
      const detail = status
        ? `서버에서 요청을 처리하지 못했어요. (오류 ${status})\n잠시 후 다시 시도하거나, 다시 로그인해 주세요.`
        : '서버에 연결하지 못했어요. 네트워크 상태를 확인해 주세요.';
      Alert.alert('문제를 불러오지 못했어요', detail);
    } finally {
      setStarting(false);
    }
  };

  // 표시값: 실제 데이터 우선, 없으면 목업 숫자로 폴백
  const displayStreak = stats?.currentStreak ?? (streak || 7);
  const solvedCount = stats?.totalSolved ?? 42;
  const accuracy = stats?.accuracyRate ?? 68;
  const tier = profile?.tier ? TIER_KO[profile.tier] ?? profile.tier : '브론즈'; // 실제 티어(/api/users/me)

  // 오늘의 목표(문제 갯수 기준): 하루 목표 문제 수 대비 "오늘" 푼 문제 수
  const dailyGoal = 3;
  const goalSolved = Math.min(todayCount, dailyGoal);
  const goalPct = Math.round((goalSolved / dailyGoal) * 100);

  return (
    <ScrollView style={styles.container} contentContainerStyle={[styles.content, {paddingTop: insets.top + 8}]}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => Alert.alert('메뉴', '메뉴는 준비 중이에요.')} hitSlop={8}>
          <MaterialIcons name="menu" size={26} color={colors.text} />
        </TouchableOpacity>
        <View style={styles.brandWrap}>
          <Text style={styles.brand}>하루코딩</Text>
          <Text style={styles.brandTag}> {'</>'}</Text>
        </View>
        <TouchableOpacity onPress={() => Alert.alert('알림', '새로운 알림이 없어요.')} hitSlop={8}>
          <View>
            <MaterialIcons name="notifications-none" size={26} color={colors.text} />
            <View style={styles.bellDot} />
          </View>
        </TouchableOpacity>
      </View>

      {/* 오늘의 목표 (컴팩트) */}
      <View style={styles.goalCard}>
        <View style={styles.goalTopRow}>
          <Text style={styles.goalLabel}>오늘의 목표</Text>
          <View style={styles.streakChip}>
            <MaterialCommunityIcons name="fire" size={14} color="#FF6B35" />
            <Text style={styles.streakChipText}>{displayStreak}일 연속</Text>
          </View>
        </View>
        <View style={styles.goalMidRow}>
          <View style={styles.goalValueRow}>
            <Text style={styles.goalValue}>{goalSolved}</Text>
            <Text style={styles.goalUnit}> / {dailyGoal}문제</Text>
          </View>
          <Text style={styles.goalDone}>
            {goalPct >= 100 ? '목표 달성! 🎉' : `${dailyGoal - goalSolved}문제 남음`}
          </Text>
        </View>
        <View style={styles.goalBarTrack}>
          <View style={[styles.goalBarFill, {width: `${goalPct}%`}]} />
        </View>
      </View>

      {/* 오늘의 문제 */}
      <View style={styles.sectionHead}>
        <Text style={styles.sectionTitle}>오늘의 문제</Text>
        <TouchableOpacity style={styles.moreBtn} onPress={() => Alert.alert('더보기', '문제 은행 탭에서 더 풀어보세요!')}>
          <Text style={styles.moreText}>더보기</Text>
          <MaterialIcons name="chevron-right" size={18} color={colors.subText} />
        </TouchableOpacity>
      </View>
      <View style={styles.problemCard}>
        <View style={styles.todayRow}>
          <View style={styles.problemIconBox}>
            <MaterialIcons name="bolt" size={26} color={colors.primary} />
          </View>
          <View style={styles.todayTextWrap}>
            <Text style={styles.todayTitle}>오늘의 추천 문제</Text>
            <Text style={styles.todaySub}>지금 실력에 맞는 문제를 받아보세요</Text>
          </View>
        </View>
        <TouchableOpacity
          style={[styles.solveBtn, starting && styles.solveBtnDisabled]}
          onPress={handleStartSet}
          disabled={starting}>
          {starting ? (
            <ActivityIndicator color="#FFF" />
          ) : (
            <Text style={styles.solveBtnText}>오늘의 문제 풀기!</Text>
          )}
        </TouchableOpacity>
      </View>

      {/* 나의 실력 */}
      <View style={styles.sectionHead}>
        <Text style={styles.sectionTitle}>나의 실력</Text>
        <TouchableOpacity style={styles.moreBtn} onPress={() => Alert.alert('전체 통계', '학습 통계 탭에서 확인하세요!')}>
          <Text style={styles.moreText}>전체 통계</Text>
          <MaterialIcons name="chevron-right" size={18} color={colors.subText} />
        </TouchableOpacity>
      </View>
      <View style={styles.statsCard}>
        <Stat icon="event-available" iconColor="#26C281" value={`${displayStreak}일`} label="연속 도전" colors={colors} fs={fontScale} />
        <View style={styles.statDivider} />
        <Stat icon="bar-chart" iconColor="#3B82F6" value={String(solvedCount)} label="문제 해결" colors={colors} fs={fontScale} />
        <View style={styles.statDivider} />
        <Stat icon="military-tech" iconColor="#CD7F32" value={tier} label="현재 티어" colors={colors} fs={fontScale} />
        <View style={styles.statDivider} />
        <Stat icon="pie-chart" iconColor="#6C5CE7" value={`${accuracy}%`} label="정답률" colors={colors} fs={fontScale} />
      </View>

      {/* 추천 학습 주제 */}
      <View style={styles.sectionHead}>
        <Text style={styles.sectionTitle}>추천 학습 주제</Text>
        <TouchableOpacity style={styles.moreBtn} onPress={() => Alert.alert('더보기', '더 많은 주제가 곧 추가돼요!')}>
          <Text style={styles.moreText}>더보기</Text>
          <MaterialIcons name="chevron-right" size={18} color={colors.subText} />
        </TouchableOpacity>
      </View>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.topicRow}>
        {TOPICS.map(t => (
          <TouchableOpacity
            key={t.label}
            style={[styles.topicCard, {backgroundColor: t.tint}]}
            onPress={handleStartSet}>
            <View style={styles.topicIconBox}>
              <MaterialIcons name={t.icon} size={20} color={t.accent} />
            </View>
            <Text style={styles.topicLabel}>{t.label}</Text>
            <Text style={styles.topicCount}>{t.count}문제</Text>
            <View style={styles.topicBarTrack}>
              <View style={[styles.topicBarFill, {width: `${t.progress * 100}%`, backgroundColor: t.accent}]} />
            </View>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* 비로그인 안내 */}
      {!isLoggedIn && (
        <TouchableOpacity style={styles.loginBanner} onPress={() => navigation.navigate('Login')}>
          <MaterialIcons name="lock" size={20} color={colors.subText} />
          <Text style={styles.loginBannerText}>로그인하면 맞춤 추천과 학습 기록을 볼 수 있어요</Text>
          <MaterialIcons name="chevron-right" size={20} color={colors.subText} />
        </TouchableOpacity>
      )}

      <View style={styles.bottomSpacer} />
    </ScrollView>
  );
}

// 나의 실력 통계 한 칸
function Stat({icon, iconColor, value, label, colors, fs}: {
  icon: string; iconColor: string; value: string; label: string; colors: Colors; fs: number;
}) {
  const styles = makeStyles(colors, fs);
  return (
    <View style={styles.statItem}>
      <MaterialIcons name={icon} size={22} color={iconColor} />
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    content: {padding: 20, paddingBottom: 32},

    // 헤더
    header: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16},
    brandWrap: {flexDirection: 'row', alignItems: 'center'},
    brand: {fontSize: 20 * fs, fontWeight: '800', color: c.text},
    brandTag: {fontSize: 18 * fs, fontWeight: '800', color: c.primary},
    bellDot: {
      position: 'absolute', top: 1, right: 2, width: 8, height: 8,
      borderRadius: 4, backgroundColor: c.primary, borderWidth: 1.5, borderColor: c.bg,
    },

    // 히어로
    hero: {flexDirection: 'row', alignItems: 'center', marginBottom: 18},
    heroTextWrap: {flex: 1},
    heroTitle: {fontSize: 24 * fs, fontWeight: '800', color: c.text, lineHeight: 32 * fs},
    heroAccent: {color: c.primary},
    heroArt: {width: 120, height: 100, alignItems: 'center', justifyContent: 'center'},
    timerBubble: {
      position: 'absolute', top: 0, right: 0, backgroundColor: c.card,
      borderRadius: 14, paddingHorizontal: 12, paddingVertical: 6,
      shadowColor: '#000', shadowOpacity: 0.08, shadowRadius: 6, shadowOffset: {width: 0, height: 2}, elevation: 3,
    },
    timerBubbleText: {fontSize: 15 * fs, fontWeight: '800', color: c.primary},
    heroEmoji: {fontSize: 56, marginTop: 18},

    // 오늘의 목표 (컴팩트)
    goalCard: {
      backgroundColor: c.card, borderRadius: 16, padding: 14, marginBottom: 18,
      shadowColor: '#000', shadowOpacity: 0.04, shadowRadius: 8, shadowOffset: {width: 0, height: 2}, elevation: 1,
    },
    goalTopRow: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8},
    goalLabel: {fontSize: 13 * fs, fontWeight: '700', color: c.text},
    streakChip: {
      flexDirection: 'row', alignItems: 'center', gap: 4,
      backgroundColor: 'rgba(255,107,53,0.12)', borderRadius: 10, paddingHorizontal: 8, paddingVertical: 3,
    },
    streakChipText: {fontSize: 11 * fs, fontWeight: '700', color: '#FF6B35'},
    goalMidRow: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8},
    goalValueRow: {flexDirection: 'row', alignItems: 'baseline'},
    goalValue: {fontSize: 22 * fs, fontWeight: '900', color: c.primary},
    goalUnit: {fontSize: 13 * fs, fontWeight: '600', color: c.subText},
    goalDone: {fontSize: 12 * fs, color: c.subText},
    goalBarTrack: {height: 6, borderRadius: 3, backgroundColor: c.border, overflow: 'hidden'},
    goalBarFill: {height: '100%', borderRadius: 3, backgroundColor: c.primary},

    // 섹션 헤더
    sectionHead: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12},
    sectionTitle: {fontSize: 17 * fs, fontWeight: '800', color: c.text},
    moreBtn: {flexDirection: 'row', alignItems: 'center'},
    moreText: {fontSize: 13 * fs, color: c.subText},

    // 오늘의 문제 카드
    problemCard: {
      backgroundColor: c.card, borderRadius: 20, padding: 18, marginBottom: 24,
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 10, shadowOffset: {width: 0, height: 4}, elevation: 2,
    },
    problemTop: {flexDirection: 'row', marginBottom: 16},
    problemIconBox: {
      width: 52, height: 52, borderRadius: 14, backgroundColor: c.primarySoft,
      alignItems: 'center', justifyContent: 'center', marginRight: 14,
    },
    problemInfo: {flex: 1},
    diffBadge: {alignSelf: 'flex-start', borderRadius: 6, paddingHorizontal: 8, paddingVertical: 2, marginBottom: 4},
    diffBadgeText: {fontSize: 11 * fs, fontWeight: '700'},
    problemTitle: {fontSize: 16 * fs, fontWeight: '800', color: c.text, marginBottom: 4},
    problemDesc: {fontSize: 13 * fs, color: c.subText, lineHeight: 19 * fs},
    problemMetaRow: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16},
    metaItem: {flex: 1},
    metaLabel: {fontSize: 11 * fs, color: c.subText, marginBottom: 3},
    metaValueRow: {flexDirection: 'row', alignItems: 'center'},
    metaIcon: {marginRight: 3},
    metaValue: {fontSize: 13 * fs, fontWeight: '700', color: c.text},
    solveBtn: {backgroundColor: c.primary, borderRadius: 14, paddingVertical: 15, alignItems: 'center'},
    solveBtnDisabled: {opacity: 0.7},
    solveBtnText: {color: '#FFFFFF', fontSize: 15 * fs, fontWeight: '800'},

    // 오늘의 문제 (간소화 카드)
    todayRow: {flexDirection: 'row', alignItems: 'center', marginBottom: 16},
    todayTextWrap: {flex: 1},
    todayTitle: {fontSize: 16 * fs, fontWeight: '800', color: c.text, marginBottom: 2},
    todaySub: {fontSize: 13 * fs, color: c.subText},

    // 나의 실력
    statsCard: {
      flexDirection: 'row', backgroundColor: c.card, borderRadius: 20, paddingVertical: 18, marginBottom: 24,
      alignItems: 'center',
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 10, shadowOffset: {width: 0, height: 4}, elevation: 2,
    },
    statItem: {flex: 1, alignItems: 'center', gap: 4},
    statValue: {fontSize: 16 * fs, fontWeight: '800', color: c.text},
    statLabel: {fontSize: 11 * fs, color: c.subText},
    statDivider: {width: 1, height: 36, backgroundColor: c.border},

    // 추천 학습 주제
    topicRow: {gap: 12, paddingRight: 4, paddingBottom: 4},
    topicCard: {width: 130, borderRadius: 18, padding: 16},
    topicIconBox: {
      width: 40, height: 40, borderRadius: 12, alignItems: 'center', justifyContent: 'center',
      marginBottom: 12, backgroundColor: '#FFFFFF',
    },
    topicLabel: {fontSize: 14 * fs, fontWeight: '800', color: '#1A1A2E', marginBottom: 2},
    topicCount: {fontSize: 12 * fs, color: '#5A5A70', marginBottom: 12},
    topicBarTrack: {height: 5, borderRadius: 3, backgroundColor: 'rgba(0,0,0,0.08)', overflow: 'hidden'},
    topicBarFill: {height: '100%', borderRadius: 3},

    // 비로그인 배너
    loginBanner: {
      flexDirection: 'row', alignItems: 'center', backgroundColor: c.card,
      borderRadius: 14, padding: 16, gap: 10, marginTop: 4,
    },
    loginBannerText: {flex: 1, fontSize: 13 * fs, color: c.subText},

    bottomSpacer: {height: 12},
  });
}
