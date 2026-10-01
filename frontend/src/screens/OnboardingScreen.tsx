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
import {useNavigation, useRoute} from '@react-navigation/native';
import type {RouteProp} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import {useTheme, type Colors} from '../theme/ThemeContext';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {api} from '../api/apiFetch';

type CodingLevel = 'NONE' | 'SOME' | 'LOTS';

const CODING_OPTIONS: {value: CodingLevel; label: string; desc: string; icon: string}[] = [
  {value: 'NONE', label: '처음이에요', desc: '코딩이 거의 처음이에요', icon: 'child-care'},
  {value: 'SOME', label: '조금 해봤어요', desc: '기초 문법은 알고 있어요', icon: 'school'},
  {value: 'LOTS', label: '많이 해봤어요', desc: '프로젝트나 실무 경험이 있어요', icon: 'workspace-premium'},
];

const COTE_OPTIONS: {value: boolean; label: string; icon: string}[] = [
  {value: false, label: '없어요', icon: 'close'},
  {value: true, label: '있어요', icon: 'check'},
];

interface OnboardingResult {
  difficulty: string;
  reason: string;
  focusPoint: string;
}

export default function OnboardingScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const route = useRoute<RouteProp<RootStackParamList, 'Onboarding'>>();
  const {signup} = route.params;
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);
  const insets = useSafeAreaInsets();

  const [codingLevel, setCodingLevel] = useState<CodingLevel | null>(null);
  const [cotePrepared, setCotePrepared] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(false);

  const canSubmit = codingLevel !== null && cotePrepared !== null;

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setLoading(true);
    try {
      // 아직 계정이 없으므로 userId 없이 계산만 요청한다(이 엔드포인트는 인증 불필요).
      // 답변을 users에 기록하는 일은 다음 화면의 "가입하기"에서 한다.
      const result = await api.post<OnboardingResult>(
        '/api/recommend/onboarding',
        {codingLevel, cotePrepared},
        false,
      );
      navigation.replace('OnboardingResult', {
        signup,
        codingLevel: codingLevel!,
        cotePrepared: cotePrepared!,
        difficulty: result.difficulty,
        reason: result.reason,
        focusPoint: result.focusPoint,
      });
    } catch (e: any) {
      Alert.alert('오류', e.message || '잠시 후 다시 시도해주세요.');
    } finally {
      setLoading(false);
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

      {/* 헤더 */}
      <View style={styles.header}>
        <View style={styles.iconBadge}>
          <MaterialIcons name="emoji-objects" size={32} color={colors.primary} />
        </View>
        <Text style={styles.title}>나에게 맞는 학습을{'\n'}찾아볼게요</Text>
        <Text style={styles.subtitle}>두 가지만 알려주시면 맞춤 추천을 드려요</Text>
      </View>

      {/* 질문 1: 코딩 경험 */}
      <View style={styles.section}>
        <View style={styles.questionHeader}>
          <Text style={styles.questionNum}>01</Text>
          <Text style={styles.question}>코딩 경험이 어느 정도인가요?</Text>
        </View>
        <View style={styles.optionsCol}>
          {CODING_OPTIONS.map(opt => {
            const selected = codingLevel === opt.value;
            return (
              <TouchableOpacity
                key={opt.value}
                style={[styles.optionCard, selected && styles.optionCardSelected]}
                onPress={() => setCodingLevel(opt.value)}
                activeOpacity={0.7}>
                <View style={[styles.radio, selected && styles.radioSelected]}>
                  {selected && <View style={styles.radioDot} />}
                </View>
                <View style={styles.optionTextWrap}>
                  <Text style={[styles.optionLabel, selected && styles.optionLabelSelected]}>
                    {opt.label}
                  </Text>
                  <Text style={styles.optionDesc}>{opt.desc}</Text>
                </View>
                <MaterialIcons
                  name={opt.icon}
                  size={20}
                  color={selected ? colors.primary : colors.border}
                />
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* 질문 2: 코테 경험 */}
      <View style={styles.section}>
        <View style={styles.questionHeader}>
          <Text style={styles.questionNum}>02</Text>
          <Text style={styles.question}>코딩 테스트 준비 경험이 있나요?</Text>
        </View>
        <View style={styles.optionsRow}>
          {COTE_OPTIONS.map(opt => {
            const selected = cotePrepared === opt.value;
            return (
              <TouchableOpacity
                key={String(opt.value)}
                style={[styles.toggleBtn, selected && styles.toggleBtnSelected]}
                onPress={() => setCotePrepared(opt.value)}
                activeOpacity={0.7}>
                <MaterialIcons
                  name={opt.icon}
                  size={22}
                  color={selected ? colors.primary : colors.subText}
                />
                <Text style={[styles.toggleText, selected && styles.toggleTextSelected]}>
                  {opt.label}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* 완료 버튼 */}
      <TouchableOpacity
        style={[styles.submitBtn, (!canSubmit || loading) && styles.btnDisabled]}
        onPress={handleSubmit}
        disabled={!canSubmit || loading}
        activeOpacity={0.85}>
        {loading ? (
          <ActivityIndicator color="#FFF" />
        ) : (
          <>
            <Text style={styles.submitBtnText}>완료</Text>
            <MaterialIcons name="arrow-forward" size={18} color="#FFF" />
          </>
        )}
      </TouchableOpacity>

      {!canSubmit && (
        <Text style={styles.hint}>두 질문 모두 선택해야 완료할 수 있어요</Text>
      )}
    </ScrollView>
  );
}

function makeStyles(c: Colors, fs: number) {
  const selectedBg = c.primarySoft;
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    content: {paddingHorizontal: 24},
    header: {alignItems: 'center', marginBottom: 36},
    iconBadge: {
      width: 64,
      height: 64,
      borderRadius: 20,
      backgroundColor: c.primarySoft,
      alignItems: 'center',
      justifyContent: 'center',
      marginBottom: 16,
    },
    title: {
      fontSize: 24 * fs,
      fontWeight: '800',
      color: c.text,
      textAlign: 'center',
      marginBottom: 8,
      lineHeight: 32 * fs,
    },
    subtitle: {fontSize: 14 * fs, color: c.subText, textAlign: 'center'},
    section: {marginBottom: 28},
    questionHeader: {flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 14},
    questionNum: {
      fontSize: 12 * fs,
      fontWeight: '800',
      color: c.primary,
      backgroundColor: c.primarySoft,
      paddingHorizontal: 8,
      paddingVertical: 2,
      borderRadius: 6,
      overflow: 'hidden',
    },
    question: {
      fontSize: 16 * fs,
      fontWeight: '700',
      color: c.text,
      flex: 1,
    },
    optionsCol: {gap: 10},
    optionCard: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: c.card,
      borderWidth: 1.5,
      borderColor: c.border,
      borderRadius: 12,
      padding: 16,
      gap: 12,
      elevation: 1,
      shadowColor: '#000',
      shadowOpacity: 0.04,
      shadowRadius: 3,
      shadowOffset: {width: 0, height: 1},
    },
    optionCardSelected: {
      borderColor: c.primary,
      backgroundColor: selectedBg,
    },
    radio: {
      width: 20,
      height: 20,
      borderRadius: 10,
      borderWidth: 2,
      borderColor: c.border,
      alignItems: 'center',
      justifyContent: 'center',
    },
    radioSelected: {borderColor: c.primary},
    radioDot: {width: 10, height: 10, borderRadius: 5, backgroundColor: c.primary},
    optionTextWrap: {flex: 1},
    optionLabel: {fontSize: 15 * fs, fontWeight: '600', color: c.text, marginBottom: 2},
    optionLabelSelected: {color: c.primary},
    optionDesc: {fontSize: 12 * fs, color: c.subText},
    optionsRow: {flexDirection: 'row', gap: 12},
    toggleBtn: {
      flex: 1,
      paddingVertical: 18,
      backgroundColor: c.card,
      borderWidth: 1.5,
      borderColor: c.border,
      borderRadius: 12,
      alignItems: 'center',
      justifyContent: 'center',
      gap: 6,
      elevation: 1,
      shadowColor: '#000',
      shadowOpacity: 0.04,
      shadowRadius: 3,
      shadowOffset: {width: 0, height: 1},
    },
    toggleBtnSelected: {
      borderColor: c.primary,
      backgroundColor: selectedBg,
    },
    toggleText: {fontSize: 15 * fs, fontWeight: '600', color: c.subText},
    toggleTextSelected: {color: c.primary},
    submitBtn: {
      backgroundColor: c.primary,
      borderRadius: 12,
      paddingVertical: 15,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 6,
      marginTop: 8,
    },
    submitBtnText: {color: '#FFF', fontWeight: '700', fontSize: 16 * fs},
    btnDisabled: {opacity: 0.4},
    hint: {
      fontSize: 12 * fs,
      color: c.subText,
      textAlign: 'center',
      marginTop: 10,
    },
  });
}
