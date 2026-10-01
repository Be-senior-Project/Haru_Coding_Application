import React, {useCallback, useState} from 'react';
import {
  View, Text, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useNavigation, useFocusEffect} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {notificationApi, type AppNotification} from '../api/notificationApi';
import {useTheme} from '../theme/ThemeContext';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

/** 알림 목록 (HOME-005). 종 아이콘 → 이 화면. 탭하면 확인 처리(읽음)된다. */
export default function NotificationScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const insets = useSafeAreaInsets();
  const {colors} = useTheme();

  const [items, setItems] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      setItems(await notificationApi.list());
    } catch (e) {
      console.error('알림 로드 실패', e);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const handlePressItem = async (item: AppNotification) => {
    if (!item.read) {
      setItems(prev => prev.map(n => (n.id === item.id ? {...n, read: true} : n)));
      try {
        await notificationApi.markRead(item.id);
      } catch (e) {
        console.error('알림 확인 처리 실패', e);
      }
    }
  };

  const handleMarkAllRead = async () => {
    setItems(prev => prev.map(n => ({...n, read: true})));
    try {
      await notificationApi.markAllRead();
    } catch (e) {
      console.error('전체 확인 처리 실패', e);
    }
  };

  const styles = makeStyles(colors);
  const hasUnread = items.some(n => !n.read);

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
        <Text style={styles.emptyText}>알림을 불러오지 못했어요.</Text>
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
        <Text style={styles.headerTitle}>알림</Text>
        <TouchableOpacity onPress={handleMarkAllRead} disabled={!hasUnread} hitSlop={8}>
          <Text style={[styles.markAllText, !hasUnread && styles.markAllTextDisabled]}>모두 읽음</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={items}
        keyExtractor={item => String(item.id)}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <Text style={styles.emptyText}>새로운 알림이 없어요.</Text>
        }
        renderItem={({item}) => (
          <TouchableOpacity style={styles.row} onPress={() => handlePressItem(item)}>
            {!item.read && <View style={styles.unreadDot} />}
            <View style={styles.rowTextWrap}>
              <Text style={styles.rowTitle} numberOfLines={1}>{item.title}</Text>
              <Text style={styles.rowBody} numberOfLines={2}>{item.body}</Text>
            </View>
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
    markAllText: {fontSize: 13, fontWeight: '600', color: colors.primary},
    markAllTextDisabled: {color: colors.subText},
    listContent: {padding: 16, gap: 10},
    row: {
      flexDirection: 'row', alignItems: 'flex-start', gap: 8,
      backgroundColor: colors.card, borderRadius: 12, padding: 14,
      borderWidth: 1, borderColor: colors.border,
    },
    unreadDot: {
      width: 8, height: 8, borderRadius: 4, backgroundColor: colors.primary, marginTop: 5,
    },
    rowTextWrap: {flex: 1},
    rowTitle: {fontSize: 15, fontWeight: '600', color: colors.text},
    rowBody: {fontSize: 12, color: colors.subText, marginTop: 6},
    emptyText: {fontSize: 14, color: colors.subText, textAlign: 'center', marginTop: 40},
    retryBtn: {marginTop: 12, paddingHorizontal: 16, paddingVertical: 8},
    retryText: {color: colors.primary, fontWeight: '600'},
  });
