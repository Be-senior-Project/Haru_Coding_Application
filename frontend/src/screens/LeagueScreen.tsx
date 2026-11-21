import React, {useCallback, useMemo, useState} from 'react';
import {View, Text, StyleSheet, ScrollView, ActivityIndicator, RefreshControl} from 'react-native';
import {useFocusEffect} from '@react-navigation/native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import {useTheme, tierColor, TIER_KO, type Colors} from '../theme/ThemeContext';
import {leagueApi, type LeagueData} from '../api/leagueApi';

export default function LeagueScreen() {
  const insets = useSafeAreaInsets();
  const {colors, fontScale: fs} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fs), [colors, fs]);

  const [data, setData] = useState<LeagueData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(false);

  const load = async (silent = false) => {
    if (!silent) {setLoading(true);}
    setError(false);
    try {
      setData(await leagueApi.getMyLeague());
    } catch {
      setError(true);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useFocusEffect(useCallback(() => { load(); }, []));

  const onRefresh = () => {
    setRefreshing(true);
    load(true);
  };

  if (loading) {
    return (
      <View style={[styles.center, {paddingTop: insets.top}]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  if (error || !data) {
    return (
      <View style={[styles.center, {paddingTop: insets.top}]}>
        <MaterialIcons name="emoji-events" size={48} color={colors.subText} />
        <Text style={styles.emptyText}>리그 정보를 불러오지 못했어요</Text>
      </View>
    );
  }

  const tc = tierColor(data.myTier);
  const tierLabel = TIER_KO[data.myTier] ?? data.myTier;

  // 상위 3명과 내 근처 사이에 생략이 있는지 확인
  const hasGap = data.members.length > 1 &&
    data.members.some((m, i) => i > 0 && m.rank - data.members[i - 1].rank > 1);

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingTop: insets.top + 8}]}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}>

      <Text style={styles.headerTitle}>리그</Text>

      {/* 내 티어 카드 */}
      <View style={[styles.tierCard, {borderColor: tc}]}>
        <View style={[styles.tierBadge, {backgroundColor: tc}]}>
          <MaterialIcons name="emoji-events" size={28} color="#FFF" />
        </View>
        <Text style={[styles.tierLabel, {color: tc}]}>{tierLabel}</Text>
        <Text style={styles.tierSeason}>시즌 {data.season}</Text>
        <View style={styles.myStatsRow}>
          <View style={styles.myStatItem}>
            <Text style={styles.myStatValue}>{data.myRank}위</Text>
            <Text style={styles.myStatLabel}>내 순위</Text>
          </View>
          <View style={[styles.myStatDivider, {backgroundColor: colors.border}]} />
          <View style={styles.myStatItem}>
            <Text style={styles.myStatValue}>{data.myScore}</Text>
            <Text style={styles.myStatLabel}>점수</Text>
          </View>
          <View style={[styles.myStatDivider, {backgroundColor: colors.border}]} />
          <View style={styles.myStatItem}>
            <Text style={styles.myStatValue}>{data.totalMembers}명</Text>
            <Text style={styles.myStatLabel}>참가자</Text>
          </View>
        </View>
      </View>

      {/* 랭킹 리스트 */}
      <Text style={styles.sectionTitle}>랭킹</Text>
      <View style={styles.rankList}>
        {data.members.map((m, i) => {
          const isTop3 = m.rank <= 3;
          const medalColor = m.rank === 1 ? '#F5B301' : m.rank === 2 ? '#9AA5B1' : m.rank === 3 ? '#B08D57' : colors.subText;
          const showGap = i > 0 && m.rank - data.members[i - 1].rank > 1;
          return (
            <React.Fragment key={m.userId}>
              {showGap && (
                <View style={styles.gapRow}>
                  <Text style={styles.gapText}>···</Text>
                </View>
              )}
              <View style={[styles.rankItem, m.isMe && styles.rankItemMe]}>
                <View style={styles.rankNumBox}>
                  {isTop3 ? (
                    <MaterialIcons name="emoji-events" size={22} color={medalColor} />
                  ) : (
                    <Text style={styles.rankNum}>{m.rank}</Text>
                  )}
                </View>
                <View style={styles.rankInfo}>
                  <Text style={[styles.rankName, m.isMe && styles.rankNameMe]} numberOfLines={1}>
                    {m.nickname}{m.isMe ? ' (나)' : ''}
                  </Text>
                </View>
                <Text style={[styles.rankScore, m.isMe && styles.rankScoreMe]}>
                  {m.score} XP
                </Text>
              </View>
            </React.Fragment>
          );
        })}
        {data.members.length === 0 && (
          <Text style={styles.emptyRank}>아직 참가자가 없어요</Text>
        )}
      </View>

      <View style={{height: 32}} />
    </ScrollView>
  );
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    content: {padding: 20},
    center: {flex: 1, backgroundColor: c.bg, alignItems: 'center', justifyContent: 'center', gap: 12},
    emptyText: {fontSize: 14 * fs, color: c.subText},

    headerTitle: {fontSize: 22 * fs, fontWeight: '800', color: c.text, marginBottom: 20, textAlign: 'center'},

    tierCard: {
      backgroundColor: c.card, borderRadius: 20, padding: 24, alignItems: 'center',
      marginBottom: 24, borderWidth: 2,
      shadowColor: '#000', shadowOpacity: 0.06, shadowRadius: 12, shadowOffset: {width: 0, height: 4}, elevation: 3,
    },
    tierBadge: {
      width: 56, height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center', marginBottom: 12,
    },
    tierLabel: {fontSize: 24 * fs, fontWeight: '900', marginBottom: 4},
    tierSeason: {fontSize: 13 * fs, color: c.subText, marginBottom: 16},
    myStatsRow: {flexDirection: 'row', alignItems: 'center', width: '100%'},
    myStatItem: {flex: 1, alignItems: 'center', gap: 4},
    myStatValue: {fontSize: 18 * fs, fontWeight: '800', color: c.text},
    myStatLabel: {fontSize: 12 * fs, color: c.subText},
    myStatDivider: {width: 1, height: 32},

    sectionTitle: {fontSize: 17 * fs, fontWeight: '800', color: c.text, marginBottom: 12},

    rankList: {
      backgroundColor: c.card, borderRadius: 16, overflow: 'hidden',
      shadowColor: '#000', shadowOpacity: 0.04, shadowRadius: 8, shadowOffset: {width: 0, height: 2}, elevation: 1,
    },
    rankItem: {
      flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 14,
      borderBottomWidth: 1, borderBottomColor: c.border,
    },
    rankItemMe: {backgroundColor: c.primarySoft},
    rankNumBox: {width: 36, alignItems: 'center'},
    rankNum: {fontSize: 15 * fs, fontWeight: '700', color: c.subText},
    rankInfo: {flex: 1, marginLeft: 8},
    rankName: {fontSize: 15 * fs, fontWeight: '600', color: c.text},
    rankNameMe: {fontWeight: '800', color: c.primary},
    rankScore: {fontSize: 14 * fs, fontWeight: '700', color: c.subText},
    rankScoreMe: {color: c.primary},
    emptyRank: {padding: 24, textAlign: 'center', fontSize: 14 * fs, color: c.subText},
    gapRow: {alignItems: 'center', paddingVertical: 4, borderBottomWidth: 1, borderBottomColor: c.border},
    gapText: {fontSize: 16 * fs, color: c.subText, letterSpacing: 4},
  });
}
