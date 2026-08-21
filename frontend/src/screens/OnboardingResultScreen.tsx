import React, {useMemo, useState} from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  Alert,
  ActivityIndicator,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {useNavigation, useRoute} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {RouteProp} from '@react-navigation/native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import {useTheme, type Colors} from '../theme/ThemeContext';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {signup as signupApi, login as loginApi} from '../api/authApi';
import {userApi} from '../api/userApi';

// 난이도 → 테마 토큰. 색 값은 ThemeContext 한 곳에서만 관리한다.
function difficultyTone(difficulty: string, c: Colors): string {
  switch (difficulty) {
    case '입문': return c.info;
    case '초급': return c.success;
    case '중급': return c.warning;
    case '고급': return c.danger;
    default: return c.primary;
  }
}

const DIFFICULTY_ICON: Record<string, string> = {
  입문: 'star-outline',
  초급: 'eco',
  중급: 'local-fire-department',
  고급: 'bolt',
};

const DIFFICULTY_BAR_WIDTH: Record<string, number> = {
  입문: 25,
  초급: 50,
  중급: 75,
  고급: 100,
};

export default function OnboardingResultScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const route = useRoute<RouteProp<RootStackParamList, 'OnboardingResult'>>();
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);
  const insets = useSafeAreaInsets();

  const {signup, codingLevel, cotePrepared, difficulty, reason, focusPoint} = route.params;
  const diffColor = difficultyTone(difficulty, colors);
  const diffIcon = DIFFICULTY_ICON[difficulty] ?? 'star';

  const [submitting, setSubmitting] = useState(false);

  /**
   * 여기가 계정이 실제로 만들어지는 유일한 지점이다.
   * 가입 → 로그인 → 온보딩 답변 저장을 한 번에 처리해, 중간 이탈 시 DB에 흔적이 남지 않게 한다.
   */
  const handleFinish = async () => {
    if (submitting) {return;}
    setSubmitting(true);
    try {
      await signupApi(signup.email, signup.password, signup.nickname, signup.password);
      const {accessToken, refreshToken} = await loginApi(signup.email, signup.password);
      await AsyncStorage.multiSet([
        ['accessToken', accessToken],
        ['refreshToken', refreshToken],
      ]);

      // 온보딩 저장이 실패해도 계정은 이미 만들어졌다. 로그인까지 끝났으니 홈으로 보내고,
      // 답변은 나중에 다시 받을 수 있으므로 여기서 흐름을 막지 않는다.
      try {
        await userApi.saveOnboarding(codingLevel, cotePrepared);
      } catch (e) {
        console.warn('온보딩 답변 저장 실패', e);
      }

      navigation.reset({index: 0, routes: [{name: 'Main'}]});
    } catch (e: any) {
      Alert.alert(
        '가입하지 못했어요',
        e?.message || '입력하신 정보를 다시 확인해주세요.',
        [{text: '확인', onPress: () => navigation.navigate('Signup')}],
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[
        styles.content,
        {paddingTop: insets.top + 32, paddingBottom: insets.bottom + 40},
      ]}
      showsVerticalScrollIndicator={false}>

      {/* 상단 완료 일러스트 */}
      <View style={styles.heroSection}>
        <View style={[styles.heroBadge, {backgroundColor: diffColor + '20'}]}>
          <MaterialIcons name={diffIcon} size={48} color={diffColor} />
        </View>
        <Text style={styles.heroTitle}>준비가 되었어요! 🎉</Text>
        <Text style={styles.heroSubtitle}>이제 당신에게 딱 맞는 문제를 추천해드릴게요</Text>
      </View>

      {/* 추천 난이도 카드 */}
      <View style={styles.difficultyCard}>
        <View style={styles.difficultyRow}>
          <Text style={styles.difficultyLabel}>추천 난이도</Text>
          <View style={[styles.difficultyBadge, {backgroundColor: diffColor + '22'}]}>
            <MaterialIcons name={diffIcon} size={14} color={diffColor} />
            <Text style={[styles.difficultyBadgeText, {color: diffColor}]}>{difficulty}</Text>
          </View>
        </View>
        <View style={styles.difficultyBar}>
          <View
            style={[
              styles.difficultyBarFill,
              {
                backgroundColor: diffColor,
                width: `${DIFFICULTY_BAR_WIDTH[difficulty] ?? 50}%`,
              },
            ]}
          />
        </View>
      </View>

      {/* 추천 이유 카드 */}
      <View style={styles.infoCard}>
        <View style={styles.infoCardHeader}>
          <View style={[styles.infoIcon, {backgroundColor: colors.primary + '20'}]}>
            <MaterialIcons name="info-outline" size={18} color={colors.primary} />
          </View>
          <Text style={styles.infoCardTitle}>추천 이유</Text>
        </View>
        <Text style={styles.infoCardBody}>{reason}</Text>
      </View>

      {/* 집중 포인트 카드 */}
      <View style={styles.infoCard}>
        <View style={styles.infoCardHeader}>
          <View style={[styles.infoIcon, {backgroundColor: colors.warning + '20'}]}>
            <MaterialIcons name="flag" size={18} color={colors.warning} />
          </View>
          <Text style={styles.infoCardTitle}>집중 포인트</Text>
        </View>
        <Text style={styles.infoCardBody}>{focusPoint}</Text>
      </View>

      {/* 안내 메시지 */}
      <View style={styles.tipRow}>
        <MaterialIcons name="lightbulb-outline" size={16} color={colors.subText} />
        <Text style={styles.tipText}>
          학습하면서 언제든지 추천을 다시 받을 수 있어요
        </Text>
      </View>

      {/* 가입하기 버튼 — 계정 생성이 여기서 일어난다 */}
      <TouchableOpacity
        style={styles.startBtn}
        onPress={handleFinish}
        disabled={submitting}
        activeOpacity={0.85}>
        {submitting ? (
          <ActivityIndicator color="#FFF" />
        ) : (
          <>
            <Text style={styles.startBtnText}>가입하기</Text>
            <MaterialIcons name="arrow-forward" size={18} color="#FFF" />
          </>
        )}
      </TouchableOpacity>
    </ScrollView>
  );
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    content: {paddingHorizontal: 24},
    heroSection: {alignItems: 'center', marginBottom: 32},
    heroBadge: {
      width: 88,
      height: 88,
      borderRadius: 28,
      alignItems: 'center',
      justifyContent: 'center',
      marginBottom: 16,
    },
    heroTitle: {
      fontSize: 26 * fs,
      fontWeight: '800',
      color: c.text,
      marginBottom: 6,
    },
    heroSubtitle: {fontSize: 14 * fs, color: c.subText},
    difficultyCard: {
      backgroundColor: c.card,
      borderRadius: 16,
      padding: 20,
      marginBottom: 14,
      elevation: 2,
      shadowColor: '#000',
      shadowOpacity: 0.05,
      shadowRadius: 4,
      shadowOffset: {width: 0, height: 2},
    },
    difficultyRow: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      marginBottom: 14,
    },
    difficultyLabel: {fontSize: 14 * fs, fontWeight: '600', color: c.subText},
    difficultyBadge: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 4,
      paddingHorizontal: 10,
      paddingVertical: 4,
      borderRadius: 20,
    },
    difficultyBadgeText: {fontSize: 13 * fs, fontWeight: '700'},
    difficultyBar: {
      height: 8,
      backgroundColor: c.border,
      borderRadius: 4,
      overflow: 'hidden',
    },
    difficultyBarFill: {height: '100%', borderRadius: 4},
    infoCard: {
      backgroundColor: c.card,
      borderRadius: 16,
      padding: 18,
      marginBottom: 14,
      elevation: 2,
      shadowColor: '#000',
      shadowOpacity: 0.05,
      shadowRadius: 4,
      shadowOffset: {width: 0, height: 2},
    },
    infoCardHeader: {flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 12},
    infoIcon: {
      width: 32,
      height: 32,
      borderRadius: 10,
      alignItems: 'center',
      justifyContent: 'center',
    },
    infoCardTitle: {fontSize: 15 * fs, fontWeight: '700', color: c.text},
    infoCardBody: {
      fontSize: 14 * fs,
      color: c.subText,
      lineHeight: 22 * fs,
    },
    tipRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 6,
      marginBottom: 28,
      paddingHorizontal: 4,
    },
    tipText: {fontSize: 12 * fs, color: c.subText, flex: 1, lineHeight: 18 * fs},
    startBtn: {
      backgroundColor: c.primary,
      borderRadius: 14,
      paddingVertical: 16,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 6,
    },
    startBtnText: {color: '#FFF', fontWeight: '700', fontSize: 16 * fs},
  });
}
