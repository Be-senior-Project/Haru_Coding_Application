import React, {useMemo, useState} from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTheme, type Colors} from '../theme/ThemeContext';
import type {RootStackParamList} from '../navigation/AppNavigator';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

export default function SignupScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const {colors, fontScale} = useTheme();
  const styles = useMemo(() => makeStyles(colors, fontScale), [colors, fontScale]);
  const insets = useSafeAreaInsets();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [showPwConfirm, setShowPwConfirm] = useState(false);
  const [agreed, setAgreed] = useState(false);

  const mismatch = passwordConfirm.length > 0 && password !== passwordConfirm;

  const handleSignup = async () => {
    if (!name.trim() || !email.trim() || !password) {
      Alert.alert('입력 오류', '모든 항목을 입력해주세요.');
      return;
    }
    if (password !== passwordConfirm) {
      Alert.alert('입력 오류', '비밀번호가 일치하지 않습니다.');
      return;
    }
    if (!agreed) {
      Alert.alert('약관 동의', '이용약관 및 개인정보 처리방침에 동의해주세요.');
      return;
    }
    // 여기서는 서버를 부르지 않는다.
    // 예전엔 이 버튼에서 바로 가입시켜서, 온보딩 도중 이탈하면 온보딩 값이 빈 계정만 DB에 남았다.
    // 실제 가입은 온보딩 결과 화면의 "가입하기"에서 한 번에 이뤄진다.
    navigation.navigate('Onboarding', {
      signup: {email: email.trim(), password, nickname: name.trim()},
    });
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView
        contentContainerStyle={[styles.inner, {paddingTop: insets.top + 16}]}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}>
        <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()} hitSlop={8}>
          <MaterialIcons name="arrow-back" size={26} color={colors.text} />
        </TouchableOpacity>

        <Text style={styles.title}>회원가입</Text>
        <Text style={styles.subtitle}>하루코딩과 함께 시작해요</Text>

        <View style={styles.form}>
          <View style={styles.inputRow}>
            <MaterialIcons name="person-outline" size={20} color={colors.subText} style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              placeholder="이름"
              placeholderTextColor={colors.subText}
              value={name}
              onChangeText={setName}
            />
          </View>

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

          <View>
            <View style={[styles.inputRow, mismatch && styles.inputRowError]}>
              <MaterialIcons name="lock-outline" size={20} color={colors.subText} style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="비밀번호 확인"
                placeholderTextColor={colors.subText}
                value={passwordConfirm}
                onChangeText={setPasswordConfirm}
                secureTextEntry={!showPwConfirm}
              />
              <TouchableOpacity onPress={() => setShowPwConfirm(v => !v)} hitSlop={8}>
                <MaterialIcons name={showPwConfirm ? 'visibility' : 'visibility-off'} size={20} color={colors.subText} />
              </TouchableOpacity>
            </View>
            {mismatch && <Text style={styles.errorText}>비밀번호가 일치하지 않습니다.</Text>}
          </View>

          <TouchableOpacity style={styles.agreeRow} onPress={() => setAgreed(v => !v)} activeOpacity={0.7}>
            <MaterialIcons
              name={agreed ? 'check-circle' : 'radio-button-unchecked'}
              size={18}
              color={agreed ? colors.primary : colors.subText}
            />
            <Text style={styles.agreeText}>
              <Text style={styles.agreeLink}>이용약관 및 개인정보 처리방침</Text>에 동의합니다
            </Text>
          </TouchableOpacity>

          {/* 실제 가입은 온보딩 결과 화면에서 이뤄지므로 여기서는 "다음"이다 */}
          <TouchableOpacity style={styles.signupBtn} onPress={handleSignup}>
            <Text style={styles.signupBtnText}>다음</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.bottomLink} onPress={() => navigation.navigate('Login')}>
            <Text style={styles.bottomLinkText}>
              이미 계정이 있으신가요? <Text style={styles.bottomLinkBold}>로그인</Text>
            </Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function makeStyles(c: Colors, fs: number) {
  return StyleSheet.create({
    container: {flex: 1, backgroundColor: c.bg},
    inner: {paddingHorizontal: 28, paddingBottom: 48},
    backBtn: {marginBottom: 20, alignSelf: 'flex-start'},
    title: {fontSize: 28 * fs, fontWeight: '800', color: c.text, marginBottom: 6},
    subtitle: {fontSize: 14 * fs, color: c.subText, marginBottom: 32},

    form: {gap: 12},
    inputRow: {
      flexDirection: 'row', alignItems: 'center',
      backgroundColor: c.card, borderWidth: 1, borderColor: c.border, borderRadius: 12,
      paddingHorizontal: 14,
    },
    inputRowError: {borderColor: c.danger},
    inputIcon: {marginRight: 10},
    input: {flex: 1, paddingVertical: 14, fontSize: 15 * fs, color: c.text},
    errorText: {fontSize: 12 * fs, color: c.danger, marginLeft: 4, marginTop: 6},

    agreeRow: {flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 4, marginTop: 2},
    agreeText: {flex: 1, fontSize: 13 * fs, color: c.subText},
    agreeLink: {color: c.primary, fontWeight: '700'},

    signupBtn: {
      backgroundColor: c.primary, borderRadius: 12, paddingVertical: 16, alignItems: 'center', marginTop: 6,
    },
    signupBtnText: {color: '#FFF', fontWeight: '800', fontSize: 16 * fs},
    btnDisabled: {opacity: 0.6},

    bottomLink: {alignItems: 'center', marginTop: 8},
    bottomLinkText: {fontSize: 14 * fs, color: c.subText},
    bottomLinkBold: {color: c.primary, fontWeight: '700'},
  });
}
