import React, {useEffect, useMemo, useState} from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  TextInput, ScrollView, Alert, ActivityIndicator, Modal,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useNavigation, useRoute} from '@react-navigation/native';
import type {RouteProp} from '@react-navigation/native';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {problemSets} from '../data/mockProblems';
import type {Problem} from '../types/problem';
import {TYPE_LABEL, toCodeLang} from '../types/problem';
import {problemApi, type CodeRunResult} from '../api/problemApi';
import {scrapApi} from '../api/scrapApi';
import {useTheme, type Colors} from '../theme/ThemeContext';
import CodeBlock, {formatCode} from '../components/CodeBlock';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

type RouteProps = RouteProp<RootStackParamList, 'ProblemSolve'>;

// 빈칸 답 비교: 서버(ProblemService.norm)와 똑같이 모든 공백을 빼고 소문자로 비교한다.
// 앞뒤 공백만 빼던 탓에 "i % 2 ==1"이 서버에선 정답인데 칸은 빨간색으로 보였다.
function normBlank(s: string | undefined): string {
  return (s ?? '').replace(/\s+/g, '').toLowerCase();
}

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

  // AI 힌트
  const [hintVisible, setHintVisible] = useState(false);
  const [hintLoading, setHintLoading] = useState(false);
  const [hint1, setHint1] = useState<string | null>(null);
  const [hint2, setHint2] = useState<string | null>(null);
  const [hint3, setHint3] = useState<string | null>(null);
  const [hintStep, setHintStep] = useState(1);
  const [hintError, setHintError] = useState(false);

  // 코드 실행 (SOLVE-007) — 채점과 별개로 예시 입력 기준 결과만 즉시 보여준다
  const [running, setRunning] = useState(false);
  const [runResult, setRunResult] = useState<CodeRunResult | null>(null);

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

  // ── 스크랩(북마크) ──────────────────────────────────────────────
  // 목 데모 문제는 DB에 없어 스크랩할 수 없다(서버가 PROBLEM_NOT_FOUND). isReal일 때만 동작시킨다.
  const [scrapped, setScrapped] = useState(false);
  const [scrapBusy, setScrapBusy] = useState(false);

  useEffect(() => {
    if (!isReal || problem?.id == null) {
      setScrapped(false);
      return;
    }
    let alive = true;
    scrapApi
      .isScrapped(problem.id)
      .then(v => {
        if (alive) setScrapped(v);
      })
      .catch(() => {
        // 비로그인·네트워크 오류 시엔 해제 상태로 둔다. 눌렀을 때 다시 알린다.
        if (alive) setScrapped(false);
      });
    return () => {
      alive = false;
    };
  }, [isReal, problem?.id]);

  const handleToggleScrap = async () => {
    if (!isReal || problem?.id == null) {
      Alert.alert('스크랩', '연습용 문제는 스크랩할 수 없어요.');
      return;
    }
    if (scrapBusy) return;
    setScrapBusy(true);
    try {
      const next = await scrapApi.toggle(problem.id);
      setScrapped(next);
      Alert.alert('스크랩', next ? '스크랩에 저장했어요.' : '스크랩에서 뺐어요.');
    } catch (e: any) {
      Alert.alert(
        '스크랩하지 못했어요',
        e?.status === 401 || e?.status === 403
          ? '로그인이 필요해요.'
          : '잠시 후 다시 시도해주세요.',
      );
    } finally {
      setScrapBusy(false);
    }
  };

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
    setRunResult(null);
    setHintVisible(false);
    setHint1(null);
    setHint2(null);
    setHint3(null);
    setHintStep(1);
    setHintError(false);
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
        ans.every((a, i) => normBlank(fillAnswers[i]) === normBlank(a));
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

  const handleHint = async () => {
    if (!isReal) {
      Alert.alert('AI 힌트', '연습용 문제는 AI 힌트를 지원하지 않아요.');
      return;
    }
    if (hint1 != null) {
      setHintVisible(true);
      return;
    }
    setHintVisible(true);
    setHintLoading(true);
    setHintError(false);
    setHintStep(1);
    try {
      const res = await problemApi.hint(problem.id);
      setHint1(res.hint1);
      setHint2(res.hint2);
      setHint3(res.hint3);
    } catch {
      setHintError(true);
    } finally {
      setHintLoading(false);
    }
  };

  const handleRunCode = async () => {
    if (!isReal) {
      Alert.alert('실행', '예시 문제는 코드 실행을 지원하지 않아요.');
      return;
    }
    setRunning(true);
    setRunResult(null);
    try {
      const res = await problemApi.run(problem.id, buildAnswerPayload());
      setRunResult(res);
    } catch (e: any) {
      Alert.alert('실행 실패', e.message || '로그인이 필요하거나 네트워크 오류예요.');
    } finally {
      setRunning(false);
    }
  };

  const runReasonLabel = (reason: string | null): string => {
    switch (reason) {
      case 'compile_error': return '컴파일 오류';
      case 'runtime_error': return '실행 중 오류';
      case 'timeout': return '시간 초과';
      case 'output_mismatch': return '예시 출력과 달라요';
      default: return '실행할 수 없어요';
    }
  };

  // 문제 은행·오답노트·스크랩에서 한 문제만 연 경우. 세트가 아니므로 "1문제 중 1개 정답 100%" 완료 팝업은 띄우지 않는다.
  const isSingleProblem = route.params.problemId != null && !passedProblems;

  const handleNext = () => {
    if (isSingleProblem) {
      navigation.goBack();
    } else if (currentIndex < problems.length - 1) {
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
    <>
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
          <TouchableOpacity onPress={handleToggleScrap} disabled={scrapBusy} hitSlop={8}>
            <MaterialIcons
              name={scrapped ? 'bookmark' : 'bookmark-border'}
              size={22}
              color={scrapped ? colors.scrap : colors.text}
            />
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
            style={[styles.runBtn, running && styles.btnDisabled]}
            onPress={handleRunCode}
            disabled={running}>
            {running ? (
              <ActivityIndicator size="small" color={colors.text} />
            ) : (
              <>
                <MaterialIcons name="play-arrow" size={18} color={colors.text} />
                <Text style={styles.runBtnText}>실행</Text>
              </>
            )}
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.hintBtn}
            onPress={handleHint}>
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
      ) : null}

      {!submitted && runResult && (
        <View style={[styles.runResultBox, runResult.ok ? styles.correctCard : styles.wrongCard]}>
          <Text style={styles.runResultTitle}>
            {runResult.ok ? '✅ 예시 출력과 일치해요' : `⚠️ ${runReasonLabel(runResult.reason)}`}
          </Text>
          {runResult.actualOutput != null && (
            <>
              <Text style={styles.answerLabel}>실행 결과</Text>
              <CodeBlock code={runResult.actualOutput} language={toCodeLang(problem.language)} />
            </>
          )}
          {!runResult.ok && !!runResult.detail && (
            <Text style={styles.resultExplain}>{runResult.detail}</Text>
          )}
        </View>
      )}

      {submitted && (
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
            <Text style={styles.nextBtnText}>{isSingleProblem ? '목록으로' : isLastProblem ? '결과 보기' : '다음 문제 →'}</Text>
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>

    {/* AI 힌트 모달 */}
    <Modal
      visible={hintVisible}
      transparent
      animationType="fade"
      onRequestClose={() => setHintVisible(false)}>
      <View style={styles.modalOverlay}>
        <View style={styles.modalCard}>
          <View style={styles.modalHeader}>
            <MaterialIcons name="lightbulb" size={22} color={colors.warning} />
            <Text style={styles.modalTitle}>AI 힌트</Text>
            <TouchableOpacity
              style={styles.modalClose}
              onPress={() => setHintVisible(false)}
              hitSlop={8}>
              <MaterialIcons name="close" size={20} color={colors.subText} />
            </TouchableOpacity>
          </View>
          {hintLoading ? (
            <View style={styles.modalBody}>
              <ActivityIndicator size="large" color={colors.primary} />
              <Text style={styles.modalLoadingText}>힌트를 불러오고 있어요...</Text>
            </View>
          ) : hintError ? (
            <View style={styles.modalBody}>
              <Text style={styles.modalErrorText}>힌트를 가져오지 못했어요.</Text>
              <TouchableOpacity
                style={styles.modalRetryBtn}
                onPress={() => { setHint1(null); handleHint(); }}>
                <Text style={styles.modalRetryText}>다시 시도</Text>
              </TouchableOpacity>
            </View>
          ) : !hint1 ? (
            <View style={styles.modalBody}>
              <MaterialIcons name="lightbulb-outline" size={40} color={colors.subText} />
              <Text style={styles.modalErrorText}>이 문제는 힌트가 준비되지 않았어요.</Text>
            </View>
          ) : (
            <ScrollView style={styles.modalScroll} bounces={false}>
              <View style={styles.hintStepHeader}>
                <View style={[styles.hintStepBadge, {backgroundColor: colors.warningSoft}]}>
                  <Text style={[styles.hintStepBadgeText, {color: colors.warning}]}>1단계</Text>
                </View>
                <Text style={styles.hintStepLabel}>방향 힌트</Text>
              </View>
              <Text style={styles.modalHintText}>{hint1}</Text>

              {hintStep === 1 && hint2 ? (
                <TouchableOpacity
                  style={styles.moreHintBtn}
                  onPress={() => setHintStep(2)}>
                  <MaterialIcons name="expand-more" size={18} color={colors.primary} />
                  <Text style={styles.moreHintText}>더 자세한 힌트 보기</Text>
                </TouchableOpacity>
              ) : null}

              {hintStep >= 2 && hint2 ? (
                <View style={styles.hintNextSection}>
                  <View style={styles.hintStepHeader}>
                    <View style={[styles.hintStepBadge, {backgroundColor: colors.infoSoft}]}>
                      <Text style={[styles.hintStepBadgeText, {color: colors.info}]}>2단계</Text>
                    </View>
                    <Text style={styles.hintStepLabel}>구체적 힌트</Text>
                  </View>
                  <Text style={styles.modalHintText}>{hint2}</Text>
                </View>
              ) : null}

              {hintStep === 2 && hint3 ? (
                <TouchableOpacity
                  style={styles.moreHintBtn}
                  onPress={() => setHintStep(3)}>
                  <MaterialIcons name="expand-more" size={18} color={colors.primary} />
                  <Text style={styles.moreHintText}>코드 구조 힌트 보기</Text>
                </TouchableOpacity>
              ) : null}

              {hintStep >= 3 && hint3 ? (
                <View style={styles.hintNextSection}>
                  <View style={styles.hintStepHeader}>
                    <View style={[styles.hintStepBadge, {backgroundColor: colors.dangerSoft}]}>
                      <Text style={[styles.hintStepBadgeText, {color: colors.danger}]}>3단계</Text>
                    </View>
                    <Text style={styles.hintStepLabel}>코드 구조 힌트</Text>
                  </View>
                  <Text style={styles.modalHintText}>{hint3}</Text>
                </View>
              ) : null}
            </ScrollView>
          )}
        </View>
      </View>
    </Modal>
    </>
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
          normBlank(values[i]) === normBlank(correctAnswers[i]);
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
    runResultBox: {borderRadius: 14, padding: 16, marginTop: 12},
    runResultTitle: {fontSize: 15 * fs, fontWeight: '700', color: c.text, marginBottom: 8},
    answerLabel: {fontSize: 12 * fs, fontWeight: '700', color: c.text, marginBottom: 6},
    resultExplain: {fontSize: 14 * fs, color: c.text, lineHeight: 22 * fs, marginBottom: 16},
    nextBtn: {
      backgroundColor: c.primary, borderRadius: 10,
      paddingVertical: 14, alignItems: 'center',
    },
    nextBtnText: {color: c.onPrimary, fontWeight: '700', fontSize: 15 * fs},
    errorText: {fontSize: 15 * fs, color: c.subText, marginBottom: 16, textAlign: 'center'},
    // AI 힌트 모달
    modalOverlay: {
      flex: 1, backgroundColor: 'rgba(0,0,0,0.55)',
      justifyContent: 'center', alignItems: 'center', padding: 24,
    },
    modalCard: {
      backgroundColor: c.card, borderRadius: 20, width: '100%', maxHeight: '60%',
      shadowColor: '#000', shadowOpacity: 0.15, shadowRadius: 20, shadowOffset: {width: 0, height: 8}, elevation: 8,
    },
    modalHeader: {
      flexDirection: 'row', alignItems: 'center', gap: 8,
      paddingHorizontal: 20, paddingTop: 20, paddingBottom: 12,
      borderBottomWidth: 1, borderBottomColor: c.border,
    },
    modalTitle: {flex: 1, fontSize: 17 * fs, fontWeight: '800', color: c.text},
    modalClose: {padding: 4},
    modalBody: {alignItems: 'center', justifyContent: 'center', paddingVertical: 40, gap: 12},
    modalLoadingText: {fontSize: 14 * fs, color: c.subText},
    modalErrorText: {fontSize: 14 * fs, color: c.danger, textAlign: 'center'},
    modalRetryBtn: {
      backgroundColor: c.primarySoft, borderRadius: 10,
      paddingHorizontal: 20, paddingVertical: 10,
    },
    modalRetryText: {color: c.primary, fontSize: 14 * fs, fontWeight: '700'},
    modalScroll: {padding: 20},
    modalHintText: {fontSize: 15 * fs, color: c.text, lineHeight: 24 * fs, marginBottom: 8},
    hintStepHeader: {flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10},
    hintStepBadge: {borderRadius: 6, paddingHorizontal: 8, paddingVertical: 3},
    hintStepBadgeText: {fontSize: 12 * fs, fontWeight: '800'},
    hintStepLabel: {fontSize: 13 * fs, fontWeight: '700', color: c.subText},
    moreHintBtn: {
      flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4,
      backgroundColor: c.primarySoft, borderRadius: 10, paddingVertical: 12, marginTop: 8,
    },
    moreHintText: {color: c.primary, fontSize: 14 * fs, fontWeight: '700'},
    hintNextSection: {marginTop: 16, paddingTop: 16, borderTopWidth: 1, borderTopColor: c.border},
  });
}
