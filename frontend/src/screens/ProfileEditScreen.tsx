import React, {useEffect, useMemo, useState} from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  Alert, ActivityIndicator, ScrollView, KeyboardAvoidingView, Platform,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useNavigation} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {useTheme, type Colors} from '../theme/ThemeContext';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import {userApi} from '../api/userApi';

// 라벨은 사용자용, value는 백엔드 허용값(users_preferred_language_check: JAVA/PYTHON/C/JS)
const LANGUAGES: {label: string; value: string}[] = [
  {label: 'Python', value: 'PYTHON'},
  {label: 'Java', value: 'JAVA'},
  {label: 'C', value: 'C'},
];

const GOAL_MIN = 1;
const GOAL_MAX = 10;

// value는 백엔드 허용값(users_difficulty_level_check: L1~L5), 'AUTO'는 자동 추천으로 되돌리는 프론트 전용 선택지
const DIFFICULTY_LEVELS: {label: string; value: string}[] = [
  {label: '자동', value: 'AUTO'},
  {label: 'L1', value: 'L1'},
  {label: 'L2', value: 'L2'},
  {label: 'L3', value: 'L3'},
  {label: 'L4', value: 'L4'},
  {label: 'L5', value: 'L5'},
];

export default function ProfileEditScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const insets = useSafeAreaInsets();
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);

  const [nickname, setNickname] = useState('');
  const [language, setLanguage] = useState<string | null>(null);
  const [dailyGoal, setDailyGoal] = useState(3);
  const [difficulty, setDifficulty] = useState('AUTO');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    userApi.getMe()
      .then(p => {
        if (!alive) {return;}
        setNickname(p.nickname ?? '');
        setLanguage(p.preferredLanguage ?? null);
        setDailyGoal(p.dailyGoalCount ?? 3);
        setDifficulty(p.difficultyLevel ?? 'AUTO');
      })
      .catch(() => Alert.alert('오류', '프로필을 불러오지 못했어요.'))
      .finally(() => {
        if (alive) {setLoading(false);}
      });
    return () => {
      alive = false;
    };
  }, []);

  const handleSave = async () => {
    if (!nickname.trim()) {
      Alert.alert('입력 오류', '닉네임을 입력해주세요.');
      return;
    }
    setSaving(true);
    try {
      await userApi.updateMe({
        nickname: nickname.trim(),
        preferredLanguage: language ?? undefined,
        dailyGoalCount: dailyGoal,
        difficultyLevel: difficulty,
      });
      Alert.alert('저장 완료', '프로필이 수정됐어요.', [
        {text: '확인', onPress: () => navigation.goBack()},
      ]);
    } catch (e: any) {
      Alert.alert('저장 실패', e?.message || '잠시 후 다시 시도해주세요.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView
        contentContainerStyle={[styles.content, {paddingTop: insets.top + 12}]}
        keyboardShouldPersistTaps="handled">

        {/* 헤더 */}
        <View style={styles.header}>
          <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
            <MaterialIcons name="arrow-back" size={26} color={colors.text} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>프로필 편집</Text>
          <View style={styles.headerSpacer} />
        </View>

        {/* 닉네임 */}
        <Text style={styles.label}>닉네임</Text>
        <View style={styles.inputRow}>
          <MaterialIcons name="person-outline" size={20} color={colors.subText} style={styles.inputIcon} />
          <TextInput
            style={styles.input}
            value={nickname}
            onChangeText={setNickname}
            placeholder="닉네임을 입력하세요"
            placeholderTextColor={colors.subText}
            maxLength={20}
          />
        </View>

        {/* 선호 언어 */}
        <Text style={styles.label}>선호 언어</Text>
        <Text style={styles.labelSub}>추천 문제의 기본 언어로 사용돼요</Text>
        <View style={styles.langWrap}>
          {LANGUAGES.map(l => {
            const active = language === l.value;
            return (
              <TouchableOpacity
                key={l.value}
                style={[styles.langChip, active && styles.langChipActive]}
                onPress={() => setLanguage(l.value)}
                activeOpacity={0.8}>
                <Text style={[styles.langChipText, active && styles.langChipTextActive]}>{l.label}</Text>
              </TouchableOpacity>
            );
          })}
        </View>

        {/* 하루 목표 문제 수 */}
        <Text style={styles.label}>하루 목표 문제 수</Text>
        <Text style={styles.labelSub}>홈 화면의 오늘 목표와 세트 크기에 반영돼요</Text>
        <View style={styles.stepperRow}>
          <TouchableOpacity
            style={[styles.stepperBtn, dailyGoal <= GOAL_MIN && styles.btnDisabled]}
            onPress={() => setDailyGoal(g => Math.max(GOAL_MIN, g - 1))}
            disabled={dailyGoal <= GOAL_MIN}>
            <MaterialIcons name="remove" size={20} color={colors.text} />
          </TouchableOpacity>
          <Text style={styles.stepperValue}>{dailyGoal}문제</Text>
          <TouchableOpacity
            style={[styles.stepperBtn, dailyGoal >= GOAL_MAX && styles.btnDisabled]}
            onPress={() => setDailyGoal(g => Math.min(GOAL_MAX, g + 1))}
            disabled={dailyGoal >= GOAL_MAX}>
            <MaterialIcons name="add" size={20} color={colors.text} />
          </TouchableOpacity>
        </View>

        {/* 난이도 조절 */}
        <Text style={styles.label}>난이도</Text>
        <Text style={styles.labelSub}>L1(입문)~L5(고급). 자동은 풀이 이력 기반 추천을 따라요</Text>
        <View style={styles.langWrap}>
          {DIFFICULTY_LEVELS.map(d => {
            const active = difficulty === d.value;
            return (
              <TouchableOpacity
                key={d.value}
                style={[styles.langChip, active && styles.langChipActive]}
                onPress={() => setDifficulty(d.value)}
                activeOpacity={0.8}>
                <Text style={[styles.langChipText, active && styles.langChipTextActive]}>{d.label}</Text>
              </TouchableOpacity>
            );
          })}
        </View>

        {/* 저장 */}
        <TouchableOpacity
          style={[styles.saveBtn, saving && styles.btnDisabled]}
          onPress={handleSave}
          disabled={saving}>
          {saving ? <ActivityIndicator color="#FFF" /> : <Text style={styles.saveBtnText}>저장하기</Text>}
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    center: {justifyContent: 'center', alignItems: 'center'},
    content: {paddingHorizontal: 24, paddingBottom: 40},

    header: {flexDirection: 'row', alignItems: 'center', marginBottom: 24},
    headerTitle: {flex: 1, textAlign: 'center', fontSize: 18 * fs, fontWeight: '800', color: c.text},
    headerSpacer: {width: 26},

    label: {fontSize: 14 * fs, fontWeight: '800', color: c.text, marginBottom: 8, marginTop: 8},
    labelSub: {fontSize: 12 * fs, color: c.subText, marginTop: -4, marginBottom: 10},

    inputRow: {
      flexDirection: 'row', alignItems: 'center', marginBottom: 12,
      backgroundColor: c.card, borderWidth: 1, borderColor: c.border, borderRadius: 12,
      paddingHorizontal: 14,
    },
    inputIcon: {marginRight: 10},
    input: {flex: 1, paddingVertical: 14, fontSize: 15 * fs, color: c.text},

    stepperRow: {flexDirection: 'row', alignItems: 'center', gap: 16, marginBottom: 28},
    stepperBtn: {
      width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center',
      backgroundColor: c.card, borderWidth: 1, borderColor: c.border,
    },
    stepperValue: {fontSize: 16 * fs, fontWeight: '800', color: c.text, minWidth: 56, textAlign: 'center'},

    langWrap: {flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 28},
    langChip: {
      paddingHorizontal: 16, paddingVertical: 10, borderRadius: 20,
      backgroundColor: c.card, borderWidth: 1, borderColor: c.border,
    },
    langChipActive: {backgroundColor: c.primary, borderColor: c.primary},
    langChipText: {fontSize: 14 * fs, color: c.subText, fontWeight: '600'},
    langChipTextActive: {color: c.onPrimary, fontWeight: '800'},

    saveBtn: {
      backgroundColor: c.primary, borderRadius: 14, paddingVertical: 16, alignItems: 'center', marginTop: 8,
    },
    saveBtnText: {color: c.onPrimary, fontSize: 16 * fs, fontWeight: '800'},
    btnDisabled: {opacity: 0.6},
  });
}
