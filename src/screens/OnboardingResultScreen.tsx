import React, {useMemo} from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
} from 'react-native';
import {useNavigation, useRoute} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {RouteProp} from '@react-navigation/native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import {useTheme, type Colors} from '../theme/ThemeContext';
import type {RootStackParamList} from '../navigation/AppNavigator';

const DIFFICULTY_COLOR: Record<string, string> = {
  입문: '#2979FF',
  초급: '#4CAF50',
  중급: '#FF9800',
  고급: '#F44336',
};

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

  const {difficulty, reason, focusPoint} = route.params;
  const diffColor = DIFFICULTY_COLOR[difficulty] ?? colors.primary;
  const diffIcon = DIFFICULTY_ICON[difficulty] ?? 'star';

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
          <View style={[styles.infoIcon, {backgroundColor: '#FF980020'}]}>
            <MaterialIcons name="flag" size={18} color="#FF9800" />
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

      {/* 시작하기 버튼 */}
      <TouchableOpacity
        style={styles.startBtn}
        onPress={() => navigation.reset({index: 0, routes: [{name: 'Main'}]})}
        activeOpacity={0.85}>
        <Text style={styles.startBtnText}>시작하기</Text>
        <MaterialIcons name="arrow-forward" size={18} color="#FFF" />
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
