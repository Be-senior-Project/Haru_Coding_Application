import React, {useEffect, useMemo, useState} from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  TextInput, ScrollView, Alert, ActivityIndicator,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useNavigation, useRoute} from '@react-navigation/native';
import type {RouteProp} from '@react-navigation/native';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {problemSets} from '../data/mockProblems';
import type {Problem} from '../types/problem';
import {TYPE_LABEL, toCodeLang} from '../types/problem';
import {problemApi} from '../api/problemApi';
import {useTheme, type Colors} from '../theme/ThemeContext';
import CodeBlock, {formatCode} from '../components/CodeBlock';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

type RouteProps = RouteProp<RootStackParamList, 'ProblemSolve'>;

function normalizeCode(s: string): string {
  return s.replace(/\r/g, '').split('\n').map(l => l.replace(/\s+$/, '')).join('\n').trim();
}

function countBlanks(p: Problem): number {
  const m = (p.codeSkeleton ?? '').match(/\{\{BLANK_\d+\}\}/g);
  if (m) return m.length;
  return Array.isArray(p.answer) ? p.answer.length : 0;
}

function displayCode(p: Problem): string {
  const code = p.codeSkeleton ?? '';
  if (p.type === 'FILL_IN_THE_BLANK') return code.replace(/\{\{BLANK_\d+\}\}/g, '___');
  if (p.type === 'IMPLEMENTATION') return code.replace(/\{\{CORE\}\}/g, '# (여기에 코드를 작성하세요)');
  return code;
}

export default function ProblemSolveScreen() {
  const navigation = useNavigation();
  const route = useRoute<RouteProps>();
  const insets = useSafeAreaInsets();
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);

  // AI 생성문제(배열)가 전달되면 그대로 사용. problemId면 백엔드 단건 로드.
  // 둘 다 DB 실 문제(정답 숨김)라 백엔드 채점(attempt)을 쓴다.
  const passedProblems = route.params.problems;
  const isReal = route.params.problemId != null || (passedProblems?.length ?? 0) > 0;

  // 목 데모용 세트
  const mockSet = problemSets.find(s => s.id === route.params.setId) ?? problemSets[0];

  const [problems, setProblems] = useState<Problem[]>(
    passedProblems ?? (route.params.problemId != null ? [] : mockSet.problems),
  );
  const [loading, setLoading] = useState(route.params.problemId != null && !passedProblems);
  const [loadError, setLoadError] = useState(false);

  const [currentIndex, setCurrentIndex] = useState(route.params.initialIndex ?? 0);

  // 답안 상태
  const [fillAnswers, setFillAnswers] = useState<string[]>([]);
  const [codeAnswer, setCodeAnswer] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [startedAt, setStartedAt] = useState(Date.now());

  // 경과 시간 타이머 (1초마다 갱신, 문제 바뀌면 startedAt 리셋되어 자동 초기화)
  const [nowTick, setNowTick] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNowTick(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  const elapsedSec = Math.max(0, Math.floor((nowTick - startedAt) / 1000));
  const mmss = `${String(Math.floor(elapsedSec / 60)).padStart(2, '0')}:${String(elapsedSec % 60).padStart(2, '0')}`;

  // 채점 결과 (목=로컬, 실=백엔드 응답)
  const [isCorrect, setIsCorrect] = useState(false);
  const [resultAnswer, setResultAnswer] = useState<string | string[] | null>(null);
  const [resultExplain, setResultExplain] = useState('');
  const [results, setResults] = useState<{id: number; correct: boolean}[]>([]);

  const problem: Problem | undefined = problems[currentIndex];

  // 실 문제 로드 (problemId 단건일 때만; 생성문제 배열은 이미 state에 있음)
  useEffect(() => {
    if (route.params.problemId == null) return;
    let alive = true;
    setLoading(true);
    problemApi
      .get(route.params.problemId as number)
      .then(p => {
        if (alive) setProblems([p]);
      })
      .catch(() => {
        if (alive) setLoadError(true);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [isReal, route.params.problemId]);

  // 문제 전환 시 상태 초기화
  useEffect(() => {
    if (!problem) return;
    if (problem.type === 'FILL_IN_THE_BLANK') {
      setFillAnswers(Array(countBlanks(problem)).fill(''));
      setCodeAnswer('');
    } else {
      setFillAnswers([]);
      // 디버깅: 원본 코드 그대로(들여쓰기=로직이라 자동 재정렬 금지). 버그 코드 그대로 보여줘야 함.
      setCodeAnswer(problem.type === 'DEBUGGING' ? (problem.codeSkeleton ?? '') : '');
    }
    setSubmitted(false);
    setIsCorrect(false);
    setResultAnswer(null);
    setResultExplain('');
    setStartedAt(Date.now());
  }, [currentIndex, problem]);

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }
  if (loadError || !problem) {
    return (
      <View style={[styles.container, styles.center, {padding: 32}]}>
        <Text style={styles.errorText}>문제를 불러오지 못했어요.</Text>
        <TouchableOpacity style={styles.submitBtn} onPress={() => navigation.goBack()}>
          <Text style={styles.submitBtnText}>돌아가기</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const buildAnswerPayload = (): unknown =>
    problem.type === 'FILL_IN_THE_BLANK' ? fillAnswers : codeAnswer;

  const localCheck = (): boolean => {
    if (problem.type === 'FILL_IN_THE_BLANK') {
      const ans = (problem.answer as string[]) ?? [];
      return ans.length > 0 &&
        ans.every((a, i) => (fillAnswers[i] ?? '').trim().toLowerCase() === a.trim().toLowerCase());
    }
    return normalizeCode(codeAnswer) === normalizeCode(problem.answer as string);
  };

  const handleSubmit = async () => {
    const timeSpentSec = Math.round((Date.now() - startedAt) / 1000);

    if (isReal) {
      // 백엔드 채점 + 기록
      setSubmitting(true);
      try {
        const res = await problemApi.attempt(problem.id, buildAnswerPayload(), timeSpentSec);
        setIsCorrect(res.correct);
        setResultAnswer(res.correctAnswer);
        setResultExplain(res.explanation);
        setResults(prev => [...prev, {id: problem.id, correct: res.correct}]);
        const today = new Date().toISOString().split('T')[0];
        await AsyncStorage.multiSet([
          ['streak', String(res.currentStreak)],
          ['lastSolvedDate', today],
        ]);
        setSubmitted(true);
      } catch (e: any) {
        Alert.alert('제출 실패', e.message || '로그인이 필요하거나 네트워크 오류예요.');
      } finally {
        setSubmitting(false);
      }
      return;
    }

    // 목 데모: 로컬 채점
    const correct = localCheck();
    setIsCorrect(correct);
    setResultAnswer(problem.answer);
    setResultExplain(problem.explanation);
    setSubmitted(true);
    setResults(prev => [...prev, {id: problem.id, correct}]);
    if (correct) {
      const today = new Date().toISOString().split('T')[0];
      const lastDate = await AsyncStorage.getItem('lastSolvedDate');
      const savedStreak = await AsyncStorage.getItem('streak');
      const streak = savedStreak ? parseInt(savedStreak, 10) : 0;
      if (lastDate !== today) {
        const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0];
        const newStreak = lastDate === yesterday ? streak + 1 : 1;
        await AsyncStorage.setItem('streak', String(newStreak));
        await AsyncStorage.setItem('lastSolvedDate', today);
      }
    }
  };

  const handleNext = () => {
    if (currentIndex < problems.length - 1) {
      setCurrentIndex(prev => prev + 1);
    } else {
      const correctCount = results.filter(r => r.correct).length;
      Alert.alert(
        '🎉 완료!',
        `${problems.length}문제 중 ${correctCount}개 정답\n정답률 ${Math.round((correctCount / problems.length) * 100)}%`,
        [{text: '확인', onPress: () => navigation.goBack()}],
      );
    }
  };

  const isLastProblem = currentIndex === problems.length - 1;
  const progress = (currentIndex + (submitted ? 1 : 0)) / problems.length;
  const correctAnswersForHint = Array.isArray(resultAnswer) ? resultAnswer : [];

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingTop: insets.top + 16}]}>

      {/* 상단 바 */}
      <View style={styles.navRow}>
        <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
          <MaterialIcons name="arrow-back-ios-new" size={22} color={colors.text} />
        </TouchableOpacity>
        <Text style={styles.navTitle}>문제 풀기</Text>
        <View style={styles.navRight}>
          <TouchableOpacity onPress={() => Alert.alert('북마크', '북마크에 저장했어요.')} hitSlop={8}>
            <MaterialIcons name="bookmark-border" size={22} color={colors.text} />
          </TouchableOpacity>
          <TouchableOpacity onPress={() => Alert.alert('메뉴', '준비 중이에요.')} hitSlop={8}>
            <MaterialIcons name="more-vert" size={22} color={colors.text} />
          </TouchableOpacity>
        </View>
      </View>

      {/* 진행 + 타이머 */}
      <View style={styles.progressRow}>
        <Text style={styles.progressLabel}>
          문제 <Text style={styles.progressNum}>{currentIndex + 1}</Text> / {problems.length}
        </Text>
        <View style={styles.progressBarWrap}>
          <View style={[styles.progressFill, {width: `${progress * 100}%`}]} />
        </View>
        <View style={styles.timerWrap}>
          <MaterialIcons name="timer" size={15} color={colors.subText} />
          <Text style={styles.timerText}>{mmss}</Text>
        </View>
      </View>

      <View style={styles.typeBadge}>
        <Text style={styles.typeBadgeText}>{TYPE_LABEL[problem.type]}</Text>
      </View>

      <Text style={styles.title}>{problem.title}</Text>
      <Text style={styles.question}>{problem.description}</Text>

      {!!problem.constraints?.length && (
        <View style={styles.metaBox}>
          <Text style={styles.metaLabel}>제약 조건</Text>
          {problem.constraints.map((c, i) => (
            <Text key={i} style={styles.metaItem}>• {c}</Text>
          ))}
        </View>
      )}

      {!!problem.ioExample && (
        <View style={styles.metaBox}>
          <Text style={styles.metaLabel}>입출력 예시</Text>
          <Text style={styles.ioLabel}>입력</Text>
          <Text style={styles.ioValue}>{problem.ioExample.input}</Text>
          <Text style={styles.ioLabel}>출력</Text>
          <Text style={styles.ioValue}>{problem.ioExample.output}</Text>
        </View>
      )}

      {!!problem.codeSkeleton && (
        <CodeBlock code={displayCode(problem)} language={toCodeLang(problem.language)} />
      )}

      {problem.type === 'FILL_IN_THE_BLANK' ? (
        <FillBlank
          count={countBlanks(problem)}
          values={fillAnswers}
          submitted={submitted}
          correctAnswers={correctAnswersForHint}
          onChange={(i, v) =>
            setFillAnswers(prev => {
              const next = [...prev];
              next[i] = v;
              return next;
            })
          }
        />
      ) : (
        <>
          <TouchableOpacity
            style={[styles.fmtBtn, submitted && styles.btnDisabled]}
            onPress={() => setCodeAnswer(formatCode(codeAnswer, 42, problem.language))}
            disabled={submitted}>
            <Text style={styles.fmtBtnText}>⟲ 코드 정렬</Text>
          </TouchableOpacity>
          <CodeAnswer
            value={codeAnswer}
            submitted={submitted}
            placeholder={problem.type === 'DEBUGGING' ? '코드를 수정하세요' : '코드를 작성하세요'}
            onChange={setCodeAnswer}
          />
        </>
      )}

      {!submitted ? (
        <View style={styles.actionRow}>
          <TouchableOpacity
            style={styles.runBtn}
            onPress={() => Alert.alert('실행', '코드 실행 기능은 준비 중이에요.')}>
            <MaterialIcons name="play-arrow" size={18} color={colors.text} />
            <Text style={styles.runBtnText}>실행</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.hintBtn}
            onPress={() => Alert.alert('AI 힌트', '곧 제공될 기능이에요.')}>
            <MaterialIcons name="lightbulb-outline" size={18} color={colors.primary} />
            <Text style={styles.hintBtnText}>AI 힌트</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.submitBtn, styles.submitFlex, submitting && styles.btnDisabled]}
            onPress={handleSubmit}
            disabled={submitting}>
            {submitting ? (
              <ActivityIndicator color="#FFF" />
            ) : (
              <>
                <MaterialIcons name="send" size={16} color="#FFF" />
                <Text style={styles.submitBtnText}>제출하기</Text>
              </>
            )}
          </TouchableOpacity>
        </View>
      ) : (
        <View style={[styles.resultCard, isCorrect ? styles.correctCard : styles.wrongCard]}>
          <Text style={styles.resultTitle}>{isCorrect ? '🎉 정답!' : '😢 오답'}</Text>
          {!isCorrect && resultAnswer != null && (
            <>
              <Text style={styles.answerLabel}>모범 답안</Text>
              <CodeBlock
                code={Array.isArray(resultAnswer) ? resultAnswer.join('\n') : resultAnswer}
                language={toCodeLang(problem.language)}
              />
            </>
          )}
          {!!resultExplain && <Text style={styles.resultExplain}>{resultExplain}</Text>}
          <TouchableOpacity style={styles.nextBtn} onPress={handleNext}>
            <Text style={styles.nextBtnText}>{isLastProblem ? '결과 보기' : '다음 문제 →'}</Text>
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
}

// ─── 하위 컴포넌트 ────────────────────────────────────────────

function FillBlank({count, values, submitted, correctAnswers, onChange}: {
  count: number; values: string[]; submitted: boolean;
  correctAnswers: string[]; onChange: (i: number, v: string) => void;
}) {
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);
  return (
    <View style={styles.fillContainer}>
      {Array.from({length: count}).map((_, i) => {
        const hasAnswer = correctAnswers.length > i;
        const correct = submitted && hasAnswer &&
          values[i]?.trim().toLowerCase() === correctAnswers[i]?.toLowerCase();
        const wrong = submitted && !correct;
        return (
          <View key={i} style={styles.fillRow}>
            <Text style={styles.fillLabel}>빈칸 {i + 1}</Text>
            <TextInput
              style={[styles.fillInput, submitted && (correct ? styles.inputCorrect : styles.inputWrong)]}
              value={values[i] ?? ''}
              onChangeText={v => onChange(i, v)}
              editable={!submitted}
              autoCapitalize="none"
              autoCorrect={false}
              placeholder={`빈칸 ${i + 1}을 입력하세요`}
              placeholderTextColor={colors.subText}
            />
            {submitted && wrong && hasAnswer && (
              <Text style={styles.correctHint}>정답: {correctAnswers[i]}</Text>
            )}
          </View>
        );
      })}
    </View>
  );
}

function CodeAnswer({value, submitted, placeholder, onChange}: {
  value: string; submitted: boolean; placeholder: string; onChange: (v: string) => void;
}) {
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);
  return (
    <TextInput
      style={[styles.codeInput, submitted && styles.inputDisabled]}
      value={value}
      onChangeText={onChange}
      editable={!submitted}
      placeholder={placeholder}
      placeholderTextColor={colors.subText}
      multiline
      autoCapitalize="none"
      autoCorrect={false}
      autoComplete="off"
      spellCheck={false}
      textAlignVertical="top"
    />
  );
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    center: {justifyContent: 'center', alignItems: 'center'},
    content: {padding: 20, paddingBottom: 60},
    // 상단 네비 행
    navRow: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18},
    navTitle: {fontSize: 17 * fs, fontWeight: '800', color: c.text},
    navRight: {flexDirection: 'row', alignItems: 'center', gap: 14},
    // 진행 + 타이머
    progressRow: {flexDirection: 'row', alignItems: 'center', marginBottom: 20, gap: 10},
    progressLabel: {fontSize: 12 * fs, color: c.subText},
    progressNum: {color: c.primary, fontWeight: '800'},
    progressBarWrap: {flex: 1, height: 8, backgroundColor: c.border, borderRadius: 4, overflow: 'hidden'},
    progressFill: {height: '100%', backgroundColor: c.primary, borderRadius: 4},
    timerWrap: {flexDirection: 'row', alignItems: 'center', gap: 3},
    timerText: {fontSize: 12 * fs, color: c.subText, fontWeight: '700'},
    typeBadge: {
      alignSelf: 'flex-start', backgroundColor: c.primarySoft,
      borderRadius: 6, paddingHorizontal: 10, paddingVertical: 4, marginBottom: 10,
    },
    typeBadgeText: {fontSize: 12 * fs, color: c.primary, fontWeight: '600'},
    title: {fontSize: 18 * fs, fontWeight: '700', color: c.text, marginBottom: 8},
    question: {fontSize: 15 * fs, color: c.text, lineHeight: 23 * fs, marginBottom: 16},
    metaBox: {
      backgroundColor: c.card, borderRadius: 10, padding: 14, marginBottom: 16,
      borderWidth: 1, borderColor: c.border,
    },
    metaLabel: {fontSize: 12 * fs, fontWeight: '700', color: c.subText, marginBottom: 6},
    metaItem: {fontSize: 13 * fs, color: c.text, lineHeight: 20 * fs},
    ioLabel: {fontSize: 11 * fs, color: c.subText, marginTop: 4},
    ioValue: {fontSize: 13 * fs, color: c.text, fontFamily: 'monospace', marginTop: 2},
    fillContainer: {gap: 14, marginBottom: 24},
    fillRow: {gap: 6},
    fillLabel: {fontSize: 13 * fs, color: c.subText},
    fillInput: {
      borderWidth: 1, borderColor: c.border, borderRadius: 10,
      padding: 14, fontSize: 14 * fs, backgroundColor: c.card, color: c.text,
      fontFamily: 'monospace',
    },
    fmtBtn: {
      alignSelf: 'flex-end', backgroundColor: c.primarySoft,
      borderRadius: 8, paddingHorizontal: 12, paddingVertical: 7, marginBottom: 8,
    },
    fmtBtnText: {color: c.primary, fontSize: 13 * fs, fontWeight: '700'},
    codeInput: {
      borderWidth: 1, borderColor: c.border, borderRadius: 10,
      padding: 14, fontSize: 13 * fs, backgroundColor: c.card, color: c.text,
      fontFamily: 'monospace', minHeight: 140, marginBottom: 24,
    },
    inputCorrect: {borderColor: c.success, backgroundColor: c.successSoft},
    inputWrong: {borderColor: c.danger, backgroundColor: c.dangerSoft},
    inputDisabled: {backgroundColor: c.isDark ? c.bg : c.border},
    correctHint: {fontSize: 12 * fs, color: c.danger},
    // 실행 / AI 힌트 / 제출하기 행
    actionRow: {flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 8},
    runBtn: {
      flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4,
      backgroundColor: c.card, borderWidth: 1, borderColor: c.border,
      borderRadius: 12, paddingVertical: 14, paddingHorizontal: 14,
    },
    runBtnText: {color: c.text, fontSize: 14 * fs, fontWeight: '700'},
    hintBtn: {
      flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4,
      backgroundColor: c.primarySoft, borderRadius: 12, paddingVertical: 14, paddingHorizontal: 14,
    },
    hintBtnText: {color: c.primary, fontSize: 14 * fs, fontWeight: '700'},
    submitBtn: {
      backgroundColor: c.primary, borderRadius: 12,
      paddingVertical: 16, alignItems: 'center', marginTop: 8,
    },
    submitFlex: {flex: 1, flexDirection: 'row', justifyContent: 'center', gap: 6, marginTop: 0},
    btnDisabled: {opacity: 0.6},
    submitBtnText: {color: c.onPrimary, fontSize: 15 * fs, fontWeight: '800'},
    resultCard: {borderRadius: 14, padding: 20, marginTop: 8},
    correctCard: {backgroundColor: c.successSoft},
    wrongCard: {backgroundColor: c.dangerSoft},
    resultTitle: {fontSize: 20 * fs, fontWeight: '700', color: c.text, marginBottom: 10},
    answerLabel: {fontSize: 12 * fs, fontWeight: '700', color: c.text, marginBottom: 6},
    resultExplain: {fontSize: 14 * fs, color: c.text, lineHeight: 22 * fs, marginBottom: 16},
    nextBtn: {
      backgroundColor: c.primary, borderRadius: 10,
      paddingVertical: 14, alignItems: 'center',
    },
    nextBtnText: {color: c.onPrimary, fontWeight: '700', fontSize: 15 * fs},
    errorText: {fontSize: 15 * fs, color: c.subText, marginBottom: 16, textAlign: 'center'},
  });
}
