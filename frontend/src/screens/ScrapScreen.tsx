import React, {useCallback, useState} from 'react';
import {
  View, Text, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useNavigation, useFocusEffect} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {scrapApi, type Scrap} from '../api/scrapApi';
import {TYPE_LABEL, difficultyLabel} from '../types/problem';
import {useTheme} from '../theme/ThemeContext';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

/**
 * 스크랩한 문제 (문제은행 탭 > 스크랩).
 *
 * ⚠️ UI는 최소한만 만들어 둔 뼈대입니다. 디자인/레이아웃은 프론트 담당이 맡습니다.
 * 데이터 연동(로딩·빈 상태·에러·스크랩 해제·문제 풀이 화면 이동)은 동작하는 상태입니다.
 */
export default function ScrapScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const insets = useSafeAreaInsets();
  const {colors} = useTheme();

  const [scraps, setScraps] = useState<Scrap[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  // 문제은행 탭은 비로그인도 들어올 수 있는데 이 화면은 인증이 필요하다.
  // 구분하지 않으면 "불러오지 못했어요"만 떠서 앱이 고장난 것처럼 보인다.
  const [needLogin, setNeedLogin] = useState(false);

  // 상태를 미리 초기화하지 않고 결과가 나온 뒤에 바꾼다. 포커스마다 다시 불러올 때
  // 기존 화면을 유지한 채 조용히 갱신해야 목록이 깜빡이지 않는다.
  const load = useCallback(async () => {
    const token = await AsyncStorage.getItem('accessToken');
    if (!token) {
      setNeedLogin(true);
      setLoading(false);
      return;
    }

    try {
      setScraps(await scrapApi.list());
      setNeedLogin(false);
      setError(false);
    } catch (e: any) {
      // 토큰이 만료돼 갱신까지 실패한 경우도 로그인 유도로 보낸다.
      if (e?.status === 401 || e?.status === 403) {
        setNeedLogin(true);
      } else {
        console.error('스크랩 목록 로드 실패', e);
        setError(true);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // 문제 풀이 화면에서 스크랩을 해제하고 돌아올 수 있어 포커스마다 다시 불러온다.
  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const retry = () => {
    setLoading(true);
    load();
  };

  const handleUnscrap = async (item: Scrap) => {
    // 목록에서 먼저 지워 반응을 즉시 보여주고, 실패하면 그 항목만 제자리에 되돌린다.
    // 목록 전체를 이전 스냅샷으로 되돌리면 그 사이 해제에 성공한 항목까지 되살아난다.
    const index = scraps.findIndex(s => s.problemId === item.problemId);
    setScraps(prev => prev.filter(s => s.problemId !== item.problemId));
    try {
      await scrapApi.toggle(item.problemId);
    } catch (e) {
      console.error('스크랩 해제 실패', e);
      setScraps(prev => {
        if (prev.some(s => s.problemId === item.problemId)) {
          return prev;
        }
        const next = [...prev];
        next.splice(Math.min(index, next.length), 0, item);
        return next;
      });
    }
  };

  const styles = makeStyles(colors);

  let body: React.ReactNode;
  if (loading) {
    body = (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  } else if (needLogin) {
    body = (
      <View style={styles.center}>
        <MaterialIcons name="lock" size={30} color={colors.subText} />
        <Text style={styles.emptyText}>로그인하면 스크랩한 문제를 볼 수 있어요</Text>
        <TouchableOpacity onPress={() => navigation.navigate('Login')} style={styles.loginBtn}>
          <Text style={styles.loginBtnText}>로그인하기</Text>
        </TouchableOpacity>
      </View>
    );
  } else if (error) {
    body = (
      <View style={styles.center}>
        <Text style={styles.emptyText}>스크랩 목록을 불러오지 못했어요.</Text>
        <TouchableOpacity onPress={retry} style={styles.retryBtn}>
          <Text style={styles.retryText}>다시 시도</Text>
        </TouchableOpacity>
      </View>
    );
  } else {
    body = (
      <FlatList
        data={scraps}
        keyExtractor={item => String(item.scrapId)}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <Text style={styles.emptyText}>아직 스크랩한 문제가 없어요.</Text>
        }
        renderItem={({item}) => (
          // 버튼 안에 버튼을 넣으면 안드로이드에서 터치가 겹쳐, 해제를 눌렀는데
          // 문제 풀이 화면으로 넘어가곤 했다. 두 버튼을 형제로 나란히 둔다.
          <View style={styles.row}>
            <TouchableOpacity
              style={styles.rowBody}
              activeOpacity={0.7}
              onPress={() => navigation.navigate('ProblemSolve', {problemId: item.problemId})}>
              <Text style={styles.rowTitle} numberOfLines={1}>{item.title}</Text>
              <Text style={styles.rowMeta}>
                {difficultyLabel(item.difficulty)} · {TYPE_LABEL[item.type]} · {item.language}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.unscrapBtn}
              activeOpacity={0.7}
              accessibilityRole="button"
              accessibilityLabel={`${item.title} 스크랩 해제`}
              onPress={() => handleUnscrap(item)}>
              <MaterialIcons name="bookmark" size={22} color={colors.scrap} />
            </TouchableOpacity>
          </View>
        )}
      />
    );
  }

  // 로그인 안내·에러 상태에서도 돌아갈 수 있도록 헤더는 항상 그린다.
  return (
    <View style={[styles.container, {paddingTop: insets.top}]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
          <MaterialIcons name="arrow-back" size={24} color={colors.text} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>스크랩한 문제</Text>
        <View style={styles.headerSpacer} />
      </View>
      {body}
    </View>
  );
}

const makeStyles = (colors: ReturnType<typeof useTheme>['colors']) =>
  StyleSheet.create({
    container: {flex: 1, backgroundColor: colors.bg},
    center: {flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg},
    header: {
      flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
      paddingHorizontal: 16, paddingVertical: 12,
    },
    headerTitle: {fontSize: 17, fontWeight: '700', color: colors.text},
    // 뒤로가기 아이콘과 같은 폭으로 제목을 가운데 맞춘다.
    headerSpacer: {width: 24},
    listContent: {padding: 16, gap: 10},
    row: {
      flexDirection: 'row', alignItems: 'center', gap: 12,
      backgroundColor: colors.card, borderRadius: 12, padding: 14,
      borderWidth: 1, borderColor: colors.border,
    },
    rowBody: {flex: 1, paddingVertical: 2},
    // 아이콘만으로는 터치 영역이 좁아 오조작이 난다. 패딩으로 44dp 가까이 확보한다.
    unscrapBtn: {padding: 10, marginRight: -4},
    rowTitle: {fontSize: 15, fontWeight: '600', color: colors.text},
    rowMeta: {fontSize: 12, color: colors.subText, marginTop: 6},
    emptyText: {fontSize: 14, color: colors.subText, textAlign: 'center', marginTop: 40},
    retryBtn: {marginTop: 12, paddingHorizontal: 16, paddingVertical: 8},
    retryText: {color: colors.primary, fontWeight: '600'},
    loginBtn: {
      marginTop: 16, paddingHorizontal: 24, paddingVertical: 10,
      backgroundColor: colors.primary, borderRadius: 10,
    },
    loginBtnText: {color: '#FFF', fontWeight: '700', fontSize: 14},
  });
