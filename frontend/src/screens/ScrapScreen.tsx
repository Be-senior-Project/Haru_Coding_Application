import React, {useCallback, useState} from 'react';
import {
  View, Text, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator,
} from 'react-native';
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

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      setScraps(await scrapApi.list());
    } catch (e) {
      console.error('스크랩 목록 로드 실패', e);
      setError(true);
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

  const handleUnscrap = async (problemId: number) => {
    // 목록에서 먼저 지워 반응을 즉시 보여주고, 실패하면 되돌린다.
    const before = scraps;
    setScraps(prev => prev.filter(s => s.problemId !== problemId));
    try {
      await scrapApi.toggle(problemId);
    } catch (e) {
      console.error('스크랩 해제 실패', e);
      setScraps(before);
    }
  };

  const styles = makeStyles(colors);

  if (loading) {
    return (
      <View style={[styles.center, {paddingTop: insets.top}]}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  if (error) {
    return (
      <View style={[styles.center, {paddingTop: insets.top}]}>
        <Text style={styles.emptyText}>스크랩 목록을 불러오지 못했어요.</Text>
        <TouchableOpacity onPress={load} style={styles.retryBtn}>
          <Text style={styles.retryText}>다시 시도</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={[styles.container, {paddingTop: insets.top}]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
          <MaterialIcons name="arrow-back" size={24} color={colors.text} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>스크랩한 문제</Text>
        <View style={{width: 24}} />
      </View>

      <FlatList
        data={scraps}
        keyExtractor={item => String(item.scrapId)}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <Text style={styles.emptyText}>아직 스크랩한 문제가 없어요.</Text>
        }
        renderItem={({item}) => (
          <TouchableOpacity
            style={styles.row}
            onPress={() => navigation.navigate('ProblemSolve', {problemId: item.problemId})}>
            <View style={styles.rowBody}>
              <Text style={styles.rowTitle} numberOfLines={1}>{item.title}</Text>
              <Text style={styles.rowMeta}>
                {difficultyLabel(item.difficulty)} · {TYPE_LABEL[item.type]} · {item.language}
              </Text>
            </View>
            <TouchableOpacity onPress={() => handleUnscrap(item.problemId)} hitSlop={8}>
              <MaterialIcons name="bookmark" size={22} color={colors.primary} />
            </TouchableOpacity>
          </TouchableOpacity>
        )}
      />
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
    listContent: {padding: 16, gap: 10},
    row: {
      flexDirection: 'row', alignItems: 'center', gap: 12,
      backgroundColor: colors.card, borderRadius: 12, padding: 14,
      borderWidth: 1, borderColor: colors.border,
    },
    rowBody: {flex: 1},
    rowTitle: {fontSize: 15, fontWeight: '600', color: colors.text},
    rowMeta: {fontSize: 12, color: colors.subText, marginTop: 6},
    emptyText: {fontSize: 14, color: colors.subText, textAlign: 'center', marginTop: 40},
    retryBtn: {marginTop: 12, paddingHorizontal: 16, paddingVertical: 8},
    retryText: {color: colors.primary, fontWeight: '600'},
  });
