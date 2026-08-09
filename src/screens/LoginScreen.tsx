import React, {useMemo, useState} from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {useNavigation} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {GoogleSignin, statusCodes} from '@react-native-google-signin/google-signin';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTheme, type Colors} from '../theme/ThemeContext';
import type {RootStackParamList} from '../navigation/AppNavigator';
import {login, googleLogin} from '../api/authApi';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import MaterialCommunityIcons from 'react-native-vector-icons/MaterialCommunityIcons';
// TODO: 백엔드 연동 시 실제 클라이언트 ID로 교체
import {GOOGLE_WEB_CLIENT_ID} from '@env';

GoogleSignin.configure({
  webClientId: GOOGLE_WEB_CLIENT_ID,
});

export default function LoginScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);
  const insets = useSafeAreaInsets();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [keepLoggedIn, setKeepLoggedIn] = useState(true);
  const [loading, setLoading] = useState(false);

  const saveTokensAndGoHome = async (accessToken: string, refreshToken: string) => {
    await AsyncStorage.multiSet([
      ['accessToken', accessToken],
      ['refreshToken', refreshToken],
    ]);
    navigation.reset({index: 0, routes: [{name: 'Main'}]});
  };

  const handleLogin = async () => {
    if (!email.trim() || !password) {
      Alert.alert('입력 오류', '이메일과 비밀번호를 입력해주세요.');
      return;
    }
    setLoading(true);
    try {
      const {accessToken, refreshToken} = await login(email.trim(), password);
      await saveTokensAndGoHome(accessToken, refreshToken);
    } catch (e: any) {
      Alert.alert('로그인 실패', e.message || '이메일 또는 비밀번호를 확인해주세요.');
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setLoading(true);
    try {
      await GoogleSignin.hasPlayServices();
      const userInfo = await GoogleSignin.signIn();
      const idToken = userInfo.data?.idToken;
      if (!idToken) {
        throw new Error('Google 로그인에 실패했습니다.');
      }
      const {accessToken, refreshToken} = await googleLogin(idToken);
      await saveTokensAndGoHome(accessToken, refreshToken);
    } catch (e: any) {
      if (e.code !== statusCodes.SIGN_IN_CANCELLED) {
        Alert.alert('Google 로그인 실패', e.message || '다시 시도해주세요.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={[styles.inner, {paddingTop: insets.top + 56}]}>
        <View style={styles.logoBox}>
          <MaterialIcons name="code" size={34} color={colors.primary} />
        </View>
        <Text style={styles.appName}>하루코딩</Text>
        <Text style={styles.tagline}>매일 30분, 코딩 실력을 성장시키세요</Text>

        <View style={styles.form}>
          <View style={styles.inputRow}>
            <MaterialIcons name="mail-outline" size={20} color={colors.subText} style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              placeholder="이메일"
              placeholderTextColor={colors.subText}
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
            />
          </View>

          <View style={styles.inputRow}>
            <MaterialIcons name="lock-outline" size={20} color={colors.subText} style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              placeholder="비밀번호"
              placeholderTextColor={colors.subText}
              value={password}
              onChangeText={setPassword}
              secureTextEntry={!showPw}
            />
            <TouchableOpacity onPress={() => setShowPw(v => !v)} hitSlop={8}>
              <MaterialIcons name={showPw ? 'visibility' : 'visibility-off'} size={20} color={colors.subText} />
            </TouchableOpacity>
          </View>

          <TouchableOpacity style={styles.keepRow} onPress={() => setKeepLoggedIn(v => !v)} activeOpacity={0.7}>
            <MaterialIcons
              name={keepLoggedIn ? 'check-circle' : 'radio-button-unchecked'}
              size={18}
              color={keepLoggedIn ? colors.primary : colors.subText}
            />
            <Text style={styles.keepText}>로그인 상태 유지</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.loginBtn, loading && styles.btnDisabled]}
            onPress={handleLogin}
            disabled={loading}>
            {loading ? (
              <ActivityIndicator color="#FFF" />
            ) : (
              <Text style={styles.loginBtnText}>로그인</Text>
            )}
          </TouchableOpacity>

          <View style={styles.dividerRow}>
            <View style={styles.dividerLine} />
            <Text style={styles.dividerText}>또는</Text>
            <View style={styles.dividerLine} />
          </View>

          <TouchableOpacity
            style={[styles.googleBtn, loading && styles.btnDisabled]}
            onPress={handleGoogleLogin}
            disabled={loading}>
            {/* Google 브랜드 색 — 테마와 무관하게 고정 */}
            <MaterialCommunityIcons name="google" size={20} color="#4285F4" />
            <Text style={styles.googleBtnText}>Google로 계속하기</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.bottomRow}>
          <TouchableOpacity onPress={() => navigation.navigate('Signup')}>
            <Text style={styles.bottomLink}>회원가입</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => navigation.reset({index: 0, routes: [{name: 'Main'}]})}>
            <Text style={styles.bottomLinkAccent}>비회원으로 둘러보기 →</Text>
          </TouchableOpacity>
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    inner: {flex: 1, paddingHorizontal: 28, paddingBottom: 28},

    logoBox: {
      width: 64, height: 64, borderRadius: 18, backgroundColor: c.primarySoft,
      alignItems: 'center', justifyContent: 'center', alignSelf: 'center', marginBottom: 14,
    },
    appName: {fontSize: 28 * fs, fontWeight: '800', color: c.text, textAlign: 'center', marginBottom: 6},
    tagline: {fontSize: 14 * fs, color: c.subText, textAlign: 'center', marginBottom: 36},

    form: {gap: 12},
    inputRow: {
      flexDirection: 'row', alignItems: 'center',
      backgroundColor: c.card, borderWidth: 1, borderColor: c.border, borderRadius: 12,
      paddingHorizontal: 14,
    },
    inputIcon: {marginRight: 10},
    input: {flex: 1, paddingVertical: 14, fontSize: 15 * fs, color: c.text},

    keepRow: {flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 2},
    keepText: {fontSize: 13 * fs, color: c.subText},

    loginBtn: {
      backgroundColor: c.primary, borderRadius: 12, paddingVertical: 16, alignItems: 'center', marginTop: 4,
    },
    loginBtnText: {color: '#FFF', fontWeight: '800', fontSize: 16 * fs},

    dividerRow: {flexDirection: 'row', alignItems: 'center', gap: 10, marginVertical: 6},
    dividerLine: {flex: 1, height: 1, backgroundColor: c.border},
    dividerText: {fontSize: 12 * fs, color: c.subText},

    googleBtn: {
      backgroundColor: c.card, borderWidth: 1, borderColor: c.border, borderRadius: 12,
      paddingVertical: 15, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8,
    },
    googleBtnText: {color: c.text, fontWeight: '700', fontSize: 15 * fs},
    btnDisabled: {opacity: 0.6},

    bottomRow: {
      marginTop: 'auto', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingTop: 16,
    },
    bottomLink: {fontSize: 14 * fs, color: c.subText, fontWeight: '600'},
    bottomLinkAccent: {fontSize: 14 * fs, color: c.primary, fontWeight: '700'},
  });
}
