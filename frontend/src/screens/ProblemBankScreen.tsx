import React, {useEffect, useMemo, useState} from 'react';
import {View, Text, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useNavigation} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {problemSets} from '../data/mockProblems';
import type {Problem} from '../types/problem';
import {TYPE_LABEL, difficultyLabel, difficultyColor, difficultySoft} from '../types/problem';
import {problemApi} from '../api/problemApi';
import {useTheme, type Colors} from '../theme/ThemeContext';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

// 새 스키마 category/subcategory → 한글 토픽 라벨 (원본 UI 느낌)
const TOPIC_KO: Record<string, string> = {
  'Basic/Introductory': '기초/입문',
  'Algorithm/Data Structure': '알고리즘',
  'Past Exam Prep': '기출 대비',
  'DFS/BFS': '그래프 탐색',
  'Dynamic Programming': '동적 계획법',
};
const ko = (s?: string | null): string => (s ? TOPIC_KO[s] ?? s : '');

// 백엔드가 응답하지 않을 때 쓰는 폴백: 목 세트 문제 flat + 데모 세트 ID
type BankProblem = Problem & {demoSetId?: string};
const mockBank: BankProblem[] = problemSets.flatMap(set =>
  set.problems.map(p => ({...p, demoSetId: set.id})),
);

export default function ProblemBankScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const [selectedTopic, setSelectedTopic] = useState('전체');
  const [bank, setBank] = useState<BankProblem[]>([]);
  const [loading, setLoading] = useState(true);
  // null = 실제 문제 목록. 'error' = 서버 연결 실패, 'empty' = 서버에 아직 문제가 없음 (둘 다 예시 문제로 폴백)
  const [mockReason, setMockReason] = useState<'error' | 'empty' | null>(null);
  const insets = useSafeAreaInsets();
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);

  useEffect(() => {
    loadBank();
  }, []);

  const loadBank = async () => {
    setLoading(true);
    try {
      const list = await problemApi.list({limit: 100});
      if (list && list.length > 0) {
        setBank(list);
        setMockReason(null);
      } else {
        setBank(mockBank);
        setMockReason('empty');
      }
    } catch (e) {
      console.error('문제 목록 로드 실패', e);
      setBank(mockBank);
      setMockReason('error');
    } finally {
      setLoading(false);
    }
  };

  const topics = useMemo(
    () => ['전체', ...Array.from(new Set(bank.map(p => ko(p.category))))],
    [bank],
  );

  const filtered =
    selectedTopic === '전체'
      ? bank
      : bank.filter(p => ko(p.category) === selectedTopic);

  const subtitle =
    mockReason === 'error'
      ? '서버에 연결하지 못해 예시 문제를 보여드리고 있어요'
      : mockReason === 'empty'
      ? '서버에 아직 등록된 문제가 없어 예시 문제를 보여드리고 있어요'
      : `${filtered.length}개의 문제가 기다리고 있어요`;

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingTop: insets.top + 20}]}>
      <Text style={styles.title}>문제 은행</Text>
      <Text style={styles.subtitle}>{subtitle}</Text>

      {/* 내 기록 진입 — 오답 노트 / 스크랩 */}
      <View style={styles.shortcutRow}>
        <TouchableOpacity
          style={styles.shortcut}
          activeOpacity={0.85}
          onPress={() => navigation.navigate('WrongNote')}>
          <View style={[styles.shortcutIcon, {backgroundColor: colors.dangerSoft}]}>
            <MaterialIcons name="rule" size={20} color={colors.danger} />
          </View>
          <View style={styles.shortcutText}>
            <Text style={styles.shortcutLabel}>오답 노트</Text>
            <Text style={styles.shortcutSub}>틀린 문제 다시 풀기</Text>
          </View>
          <MaterialIcons name="chevron-right" size={20} color={colors.subText} />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.shortcut}
          activeOpacity={0.85}
          onPress={() => navigation.navigate('Scrap')}>
          <View style={[styles.shortcutIcon, {backgroundColor: colors.primarySoft}]}>
            <MaterialIcons name="bookmark" size={20} color={colors.primary} />
          </View>
          <View style={styles.shortcutText}>
            <Text style={styles.shortcutLabel}>스크랩</Text>
            <Text style={styles.shortcutSub}>저장해둔 문제</Text>
          </View>
          <MaterialIcons name="chevron-right" size={20} color={colors.subText} />
        </TouchableOpacity>
      </View>

      {loading ? (
        <ActivityIndicator color={colors.primary} style={styles.loadingIndicator} />
      ) : (
        <>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterScroll}>
            {topics.map(t => (
              <TouchableOpacity
                key={t}
                style={[styles.filterBtn, selectedTopic === t && styles.filterActive]}
                onPress={() => setSelectedTopic(t)}>
                <Text style={[styles.filterText, selectedTopic === t && styles.filterTextActive]}>{t}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>

          {filtered.map(p => (
            <TouchableOpacity
              key={p.id}
              style={styles.problemCard}
              activeOpacity={0.85}
              onPress={() =>
                navigation.navigate(
                  'ProblemSolve',
                  mockReason ? {setId: p.demoSetId} : {problemId: p.id},
                )
              }>
              <View style={styles.cardTop}>
                <View style={styles.badges}>
                  <View style={[styles.badge, {backgroundColor: difficultySoft(p.difficulty, colors)}]}>
                    <Text style={[styles.badgeText, {color: difficultyColor(p.difficulty, colors)}]}>
                      {difficultyLabel(p.difficulty)}
                    </Text>
                  </View>
                  <View style={styles.typeBadge}>
                    <Text style={styles.typeBadgeText}>{TYPE_LABEL[p.type]}</Text>
                  </View>
                </View>
                <Text style={styles.topicText} numberOfLines={1}>
                  {ko(p.subcategory) || ko(p.category)}
                </Text>
              </View>
              <Text style={styles.problemTitle}>{p.title}</Text>
              <Text style={styles.problemQuestion} numberOfLines={2}>{p.description}</Text>
            </TouchableOpacity>
          ))}

          {filtered.length === 0 && (
            <Text style={styles.empty}>해당 주제의 문제가 아직 없어요</Text>
          )}
        </>
      )}
    </ScrollView>
  );
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    content: {padding: 20, paddingBottom: 40},

    title: {fontSize: 22 * fs, fontWeight: '800', color: c.text, marginBottom: 2},
    subtitle: {fontSize: 13 * fs, color: c.subText, marginBottom: 16},

    // 오답 노트 · 스크랩 진입
    shortcutRow: {gap: 10, marginBottom: 20},
    shortcut: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      backgroundColor: c.card,
      borderRadius: 16,
      paddingVertical: 14,
      paddingHorizontal: 16,
      shadowColor: '#000',
      shadowOpacity: 0.05,
      shadowRadius: 8,
      shadowOffset: {width: 0, height: 3},
      elevation: 2,
    },
    shortcutIcon: {width: 38, height: 38, borderRadius: 11, alignItems: 'center', justifyContent: 'center'},
    shortcutText: {flex: 1},
    shortcutLabel: {fontSize: 15 * fs, fontWeight: '800', color: c.text},
    shortcutSub: {fontSize: 12 * fs, color: c.subText, marginTop: 2},

    filterScroll: {marginBottom: 16},
    filterBtn: {
      paddingHorizontal: 14,
      paddingVertical: 8,
      borderRadius: 20,
      backgroundColor: c.filterInactive,
      marginRight: 8,
    },
    filterActive: {backgroundColor: c.primary},
    filterText: {fontSize: 13 * fs, color: c.subText},
    filterTextActive: {color: c.onPrimary, fontWeight: '700'},

    // 문제 카드 — Home의 problemCard와 동일한 소프트 그림자/둥근 모서리
    problemCard: {
      backgroundColor: c.card,
      borderRadius: 18,
      padding: 16,
      marginBottom: 12,
      shadowColor: '#000',
      shadowOpacity: 0.05,
      shadowRadius: 10,
      shadowOffset: {width: 0, height: 4},
      elevation: 2,
    },
    cardTop: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6},
    badges: {flexDirection: 'row', gap: 6},
    badge: {paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6},
    badgeText: {fontSize: 11 * fs, fontWeight: '700'},
    typeBadge: {
      backgroundColor: c.primarySoft,
      paddingHorizontal: 8,
      paddingVertical: 3,
      borderRadius: 6,
    },
    typeBadgeText: {fontSize: 11 * fs, color: c.primary, fontWeight: '700'},
    topicText: {fontSize: 11 * fs, color: c.subText, flexShrink: 1, marginLeft: 8},
    problemTitle: {fontSize: 15 * fs, fontWeight: '800', color: c.text, marginBottom: 4},
    problemQuestion: {fontSize: 13 * fs, color: c.subText, lineHeight: 20 * fs},

    loadingIndicator: {marginTop: 40},
    empty: {textAlign: 'center', color: c.subText, marginTop: 40, fontSize: 14 * fs},
  });
}
