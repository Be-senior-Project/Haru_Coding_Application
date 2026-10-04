import React, {useCallback, useState} from 'react';
import {
  View, Text, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useNavigation, useFocusEffect} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {wrongNoteApi, type WrongNote} from '../api/wrongNoteApi';
import {TYPE_LABEL, difficultyLabel} from '../types/problem';
import {useTheme} from '../theme/ThemeContext';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

/**
 * 오답노트 (문제은행 탭 > 오답노트).
 *
 * ⚠️ UI는 최소한만 만들어 둔 뼈대입니다. 디자인/레이아웃은 프론트 담당이 맡습니다.
 * 데이터 연동(로딩·빈 상태·에러·문제 풀이 화면 이동)은 동작하는 상태이니,
 * 목록 행 모양만 디자인 시안에 맞게 교체하면 됩니다.
 */
export default function WrongNoteScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const insets = useSafeAreaInsets();
  const {colors} = useTheme();

  const [notes, setNotes] = useState<WrongNote[]>([]);
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
      setNotes(await wrongNoteApi.list());
      setNeedLogin(false);
      setError(false);
    } catch (e: any) {
      // 토큰이 만료돼 갱신까지 실패한 경우도 로그인 유도로 보낸다.
      if (e?.status === 401 || e?.status === 403) {
        setNeedLogin(true);
      } else {
        console.error('오답노트 로드 실패', e);
        setError(true);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // 문제를 풀고 돌아오면 목록이 바뀔 수 있어 포커스마다 다시 불러온다.
  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const retry = () => {
    setLoading(true);
    load();
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
        <Text style={styles.emptyText}>로그인하면 틀린 문제를 모아볼 수 있어요</Text>
        <TouchableOpacity onPress={() => navigation.navigate('Login')} style={styles.loginBtn}>
          <Text style={styles.loginBtnText}>로그인하기</Text>
        </TouchableOpacity>
      </View>
    );
  } else if (error) {
    body = (
      <View style={styles.center}>
        <Text style={styles.emptyText}>오답노트를 불러오지 못했어요.</Text>
        <TouchableOpacity onPress={retry} style={styles.retryBtn}>
          <Text style={styles.retryText}>다시 시도</Text>
        </TouchableOpacity>
      </View>
    );
  } else {
    body = (
      <FlatList
        data={notes}
        keyExtractor={item => String(item.problemId)}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <Text style={styles.emptyText}>아직 틀린 문제가 없어요.</Text>
        }
        renderItem={({item}) => (
          <TouchableOpacity
            style={styles.row}
            onPress={() => navigation.navigate('ProblemSolve', {problemId: item.problemId})}>
            <Text style={styles.rowTitle} numberOfLines={1}>{item.title}</Text>
            <Text style={styles.rowMeta}>
              {difficultyLabel(item.difficulty)} · {TYPE_LABEL[item.type]} · {item.language}
            </Text>
          </TouchableOpacity>
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
        <Text style={styles.headerTitle}>오답노트</Text>
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
      backgroundColor: colors.card, borderRadius: 12, padding: 14,
      borderWidth: 1, borderColor: colors.border,
    },
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
