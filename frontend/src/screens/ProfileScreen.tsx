import React, {useCallback, useMemo, useState} from 'react';
import {
  View, Text, StyleSheet, ScrollView, Image,
  TouchableOpacity, Switch, ActivityIndicator, Alert,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTheme, tierFromLevel, type Colors, type FontSizeKey} from '../theme/ThemeContext';
import {useNavigation, useFocusEffect} from '@react-navigation/native';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {BottomTabNavigationProp} from '@react-navigation/bottom-tabs';
import {RootStackParamList, type TabParamList} from '../navigation/AppNavigator';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import MaterialCommunityIcons from 'react-native-vector-icons/MaterialCommunityIcons';
import {userApi, type UserProfile} from '../api/userApi';
import {statsApi} from '../api/statsApi';
import {logout as logoutApi} from '../api/authApi';

const FONT_SIZE_OPTIONS: {key: FontSizeKey; size: number}[] = [
  {key: 'small', size: 13},
  {key: 'medium', size: 17},
  {key: 'large', size: 22},
];


export default function ProfileScreen() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  // 연속 학습일은 홈·학습 통계와 같은 출처(/api/stats/me)를 쓴다.
  // 프로필의 streakDays는 서버에서 갱신되지 않아 늘 0이었다.
  const [streak, setStreak] = useState<number | null>(null);
  const [hasToken, setHasToken] = useState(false);
  const [loading, setLoading] = useState(false);
  const insets = useSafeAreaInsets();
  const {colors, isDark, toggleTheme, fontScale, fontSizeKey, setFontSize} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  // 같은 navigation 객체지만, 탭 이동(학습 통계)은 탭 타입으로 부른다.
  const tabNavigation = useNavigation<BottomTabNavigationProp<TabParamList>>();

  useFocusEffect(
    useCallback(() => {
      loadProfile();
    }, []),
  );

  const loadProfile = async () => {
    const token = await AsyncStorage.getItem('accessToken');
    setHasToken(!!token);
    if (!token) {return;}
    setLoading(true);
    try {
      setProfile(await userApi.getMe());
    } catch (e) {
      console.error('프로필 로드 실패', e);
    }
    try {
      setStreak((await statsApi.getMyStats()).currentStreak);
    } catch {
      setStreak(null);
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    // 서버의 refresh token부터 폐기한다. 이게 빠지면 앱에서만 지워지고 DB에는 그대로 남는다.
    const token = await AsyncStorage.getItem('accessToken');
    if (token) {
      try {
        await logoutApi(token);
      } catch (e) {
        // 네트워크·토큰 만료로 실패해도 로그아웃 자체는 진행한다(앱에 갇히지 않도록).
        console.warn('서버 로그아웃 실패, 로컬 토큰만 삭제합니다', e);
      }
    }
    await AsyncStorage.multiRemove(['accessToken', 'refreshToken']);
    setHasToken(false);
    setProfile(null);
    setStreak(null);
    navigation.reset({index: 0, routes: [{name: 'Login'}]});
  };

  const confirmLogout = () => {
    Alert.alert('로그아웃', '정말 로그아웃할까요?', [
      {text: '취소', style: 'cancel'},
      {text: '로그아웃', style: 'destructive', onPress: handleLogout},
    ]);
  };

  const handleWithdraw = async () => {
    try {
      await userApi.deleteMe();
      await AsyncStorage.multiRemove(['accessToken', 'refreshToken']);
      setHasToken(false);
      setProfile(null);
      setStreak(null);
      navigation.reset({index: 0, routes: [{name: 'Login'}]});
    } catch (e) {
      Alert.alert('탈퇴 실패', '잠시 후 다시 시도해 주세요.');
    }
  };

  const confirmWithdraw = () => {
    Alert.alert(
      '탈퇴하기',
      '탈퇴하면 모든 학습 기록이 삭제되고 복구할 수 없어요. 정말 탈퇴할까요?',
      [
        {text: '취소', style: 'cancel'},
        {text: '탈퇴하기', style: 'destructive', onPress: handleWithdraw},
      ],
    );
  };

  const xpProgress = profile ? profile.xp % 100 : 0;
  const tier = profile ? tierFromLevel(profile.level) : null;
  const streakDays = streak ?? 0;

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingTop: insets.top + 8}]}>

      {/* 헤더 */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>마이페이지</Text>
        <View style={styles.headerIcons}>
          <TouchableOpacity onPress={() => navigation.navigate('Notification')} hitSlop={8}>
            <MaterialIcons name="notifications-none" size={22} color={colors.text} />
          </TouchableOpacity>
        </View>
      </View>

      {loading ? (
        <ActivityIndicator color={colors.primary} style={styles.loadingIndicator} />
      ) : hasToken && profile ? (
        <>
          {/* 프로필 카드 */}
          <View style={styles.profileCard}>
            <View style={styles.avatar}>
              {profile.profileImageUrl ? (
                <Image source={{uri: profile.profileImageUrl}} style={styles.avatarImg} />
              ) : (
                <MaterialIcons name="person" size={40} color={colors.subText} />
              )}
            </View>
            <View style={styles.profileInfo}>
              <View style={styles.nameRow}>
                <Text style={styles.username}>{profile.nickname}</Text>
              </View>
              <Text style={styles.userMeta}>Lv.{profile.level} · {profile.xp.toLocaleString()} XP</Text>
              <View style={styles.expBar}>
                <View style={[styles.expFill, {width: `${xpProgress}%`}]} />
              </View>
              <Text style={styles.expText}>다음 레벨까지 {100 - xpProgress} XP</Text>
            </View>
            {tier && (
              <View style={styles.tierBadge}>
                <MaterialCommunityIcons name="shield-star" size={26} color={tier.color} />
                <Text style={styles.tierText}>{tier.label}</Text>
              </View>
            )}
          </View>

          {/* 연속 학습 배너: 0일이면 시작을 권하고 홈(오늘의 문제)으로, 1일 이상이면 학습 통계로 */}
          <TouchableOpacity
            style={styles.streakBanner}
            activeOpacity={0.85}
            onPress={() => tabNavigation.navigate(streakDays > 0 ? '학습 통계' : '홈')}>
            <MaterialCommunityIcons name="fire" size={22} color={streakDays > 0 ? colors.streak : colors.subText} />
            <View style={styles.streakTextWrap}>
              <Text style={styles.streakTitle}>
                {streakDays > 0 ? `${streakDays}일 연속 학습 중!` : '오늘 문제를 풀고 연속 학습을 시작하세요!'}
              </Text>
              <Text style={styles.streakSub}>
                {streakDays > 0
                  ? `내일도 풀면 ${streakDays + 1}일! 하루라도 쉬면 0일부터 다시 시작해요`
                  : '매일 풀면 하루씩 늘고, 하루라도 쉬면 0일로 돌아가요'}
              </Text>
            </View>
            <MaterialIcons name="chevron-right" size={22} color={colors.subText} />
          </TouchableOpacity>

          {/* 핵심 통계 4종 (실제 값) */}
          <View style={styles.statRow}>
            <Stat icon="article" color={colors.primary} value={`${profile.totalSolved}개`} label="푼 문제" colors={colors} fs={fontScale} />
            <View style={styles.statDivider} />
            <Stat icon="check-circle" color={colors.success} value={`${profile.accuracyRate}%`} label="정답률" colors={colors} fs={fontScale} />
            <View style={styles.statDivider} />
            <Stat icon="local-fire-department" color={colors.streak} value={`${streakDays}일`} label="연속 학습" colors={colors} fs={fontScale} />
            <View style={styles.statDivider} />
            <Stat icon="stars" color={colors.warning} value={profile.xp.toLocaleString()} label="획득 XP" colors={colors} fs={fontScale} />
          </View>
        </>
      ) : (
        /* 비로그인 */
        <View style={styles.loginCard}>
          <MaterialIcons name="lock" size={30} color={colors.subText} />
          <Text style={styles.loginText}>로그인하면 내 정보를 볼 수 있어요</Text>
          <TouchableOpacity style={styles.loginBtn} onPress={() => navigation.navigate('Login')}>
            <Text style={styles.loginBtnText}>로그인하기</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* 내 정보 (다크모드·글자크기는 실제 동작) */}
      <Text style={styles.sectionTitle}>내 정보</Text>
      <View style={styles.menuCard}>
        <MenuRow icon="person-outline" iconColor={colors.subText} label="프로필 관리" onPress={() => navigation.navigate('ProfileEdit')} colors={colors} fs={fontScale} />
        <MenuRow icon="notifications-none" iconColor={colors.subText} label="알림" onPress={() => navigation.navigate('Notification')} colors={colors} fs={fontScale} />
        <MenuRow
          icon="dark-mode" iconColor={colors.subText} label="다크 모드" colors={colors} fs={fontScale}
          right={<Switch value={isDark} onValueChange={toggleTheme} trackColor={{true: colors.primary}} />}
        />
        <MenuRow
          icon="text-fields" iconColor={colors.subText} label="글자 크기" last colors={colors} fs={fontScale}
          right={
            <View style={styles.fontSizeRow}>
              {FONT_SIZE_OPTIONS.map(opt => (
                <TouchableOpacity
                  key={opt.key}
                  style={[styles.fontSizeBtn, fontSizeKey === opt.key && styles.fontSizeBtnActive]}
                  onPress={() => setFontSize(opt.key)}>
                  <Text style={[
                    styles.fontSizeBtnText, {fontSize: opt.size},
                    fontSizeKey === opt.key && styles.fontSizeBtnTextActive,
                  ]}>가</Text>
                </TouchableOpacity>
              ))}
            </View>
          }
        />
      </View>

      {/* 계정 (로그인 상태에서만 노출) */}
      {hasToken && (
        <View style={styles.menuCard}>
          <MenuRow icon="logout" iconColor={colors.subText} label="로그아웃" onPress={confirmLogout} colors={colors} fs={fontScale} />
          <MenuRow icon="person-remove" iconColor={colors.danger} label="탈퇴하기" danger onPress={confirmWithdraw} last colors={colors} fs={fontScale} />
        </View>
      )}

      <View style={styles.bottomSpacer} />
    </ScrollView>
  );
}

function Stat({icon, color, value, label, colors, fs}: {
  icon: string; color: string; value: string; label: string; colors: Colors; fs: number;
}) {
  const styles = makeStyles(colors, fs);
  return (
    <View style={styles.statItem}>
      <MaterialIcons name={icon} size={22} color={color} />
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function MenuRow({icon, iconColor, label, right, onPress, last, danger, colors, fs}: {
  icon: string; iconColor: string; label: string; right?: React.ReactNode;
  onPress?: () => void; last?: boolean; danger?: boolean; colors: Colors; fs: number;
}) {
  const styles = makeStyles(colors, fs);
  const body = (
    <View style={[styles.menuRow, last && styles.noBorder]}>
      <MaterialIcons name={icon} size={20} color={iconColor} style={styles.menuIcon} />
      <Text style={[styles.menuLabel, danger && styles.menuLabelDanger]}>{label}</Text>
      {right ?? <MaterialIcons name="chevron-right" size={20} color={colors.subText} />}
    </View>
  );
  return onPress
    ? <TouchableOpacity onPress={onPress} activeOpacity={0.7}>{body}</TouchableOpacity>
    : body;
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    content: {padding: 20, paddingBottom: 32},
    loadingIndicator: {marginTop: 40},
    bottomSpacer: {height: 16},

    header: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18},
    headerTitle: {fontSize: 19 * fs, fontWeight: '800', color: c.text},
    headerIcons: {flexDirection: 'row', alignItems: 'center', gap: 16},

    // 프로필 카드
    profileCard: {
      flexDirection: 'row', alignItems: 'center', backgroundColor: c.card, borderRadius: 20, padding: 18, marginBottom: 14,
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 10, shadowOffset: {width: 0, height: 4}, elevation: 2,
    },
    avatar: {
      width: 64, height: 64, borderRadius: 32, backgroundColor: c.primarySoft,
      alignItems: 'center', justifyContent: 'center', marginRight: 14, overflow: 'hidden',
    },
    avatarImg: {width: 64, height: 64},
    profileInfo: {flex: 1},
    nameRow: {flexDirection: 'row', alignItems: 'center', gap: 6},
    username: {fontSize: 18 * fs, fontWeight: '800', color: c.text},
    userMeta: {fontSize: 12 * fs, color: c.subText, marginTop: 2, marginBottom: 8},
    expBar: {height: 7, backgroundColor: c.border, borderRadius: 4, overflow: 'hidden', marginBottom: 4},
    expFill: {height: '100%', backgroundColor: c.primary, borderRadius: 4},
    expText: {fontSize: 11 * fs, color: c.subText},
    tierBadge: {alignItems: 'center', marginLeft: 8},
    tierText: {fontSize: 11 * fs, fontWeight: '700', color: c.text, marginTop: 2},

    // 연속 학습 배너
    streakBanner: {
      flexDirection: 'row', alignItems: 'center', gap: 12,
      backgroundColor: c.streakSoft, borderRadius: 16, padding: 16, marginBottom: 14,
    },
    streakTextWrap: {flex: 1},
    streakTitle: {fontSize: 14 * fs, fontWeight: '800', color: c.text},
    streakSub: {fontSize: 12 * fs, color: c.subText, marginTop: 2},

    // 통계 4종
    statRow: {
      flexDirection: 'row', alignItems: 'center', backgroundColor: c.card, borderRadius: 20, paddingVertical: 18, marginBottom: 24,
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 10, shadowOffset: {width: 0, height: 4}, elevation: 2,
    },
    statItem: {flex: 1, alignItems: 'center', gap: 4},
    statValue: {fontSize: 15 * fs, fontWeight: '800', color: c.text},
    statLabel: {fontSize: 11 * fs, color: c.subText},
    statDivider: {width: 1, height: 34, backgroundColor: c.border},

    // 비로그인
    loginCard: {
      backgroundColor: c.card, borderRadius: 20, padding: 28, alignItems: 'center', gap: 10, marginBottom: 24,
    },
    loginText: {fontSize: 14 * fs, color: c.subText, textAlign: 'center'},
    loginBtn: {backgroundColor: c.primary, borderRadius: 12, paddingVertical: 12, paddingHorizontal: 32, marginTop: 4},
    loginBtnText: {color: c.onPrimary, fontWeight: '800', fontSize: 14 * fs},

    // 섹션 + 메뉴 리스트
    sectionTitle: {fontSize: 16 * fs, fontWeight: '800', color: c.text, marginTop: 4, marginBottom: 12},
    menuCard: {
      backgroundColor: c.card, borderRadius: 16, overflow: 'hidden', marginBottom: 20,
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 8, shadowOffset: {width: 0, height: 3}, elevation: 2,
    },
    menuRow: {
      flexDirection: 'row', alignItems: 'center', paddingVertical: 15, paddingHorizontal: 16,
      borderBottomWidth: 1, borderBottomColor: c.border, minHeight: 54,
    },
    noBorder: {borderBottomWidth: 0},
    menuIcon: {marginRight: 14},
    menuLabel: {flex: 1, fontSize: 14 * fs, color: c.text},
    menuLabelDanger: {color: c.danger, fontWeight: '700'},

    // 글자 크기 버튼
    fontSizeRow: {flexDirection: 'row', gap: 8},
    fontSizeBtn: {
      width: 34, height: 34, borderRadius: 9, borderWidth: 1.5, borderColor: c.border,
      alignItems: 'center', justifyContent: 'center', backgroundColor: c.bg,
    },
    fontSizeBtnActive: {borderColor: c.primary, backgroundColor: c.primarySoft},
    fontSizeBtnText: {color: c.subText, fontWeight: '700'},
    fontSizeBtnTextActive: {color: c.primary},
  });
}
