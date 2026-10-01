import React, {useMemo, useRef, useState} from 'react';
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
  ScrollView,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTheme, type Colors} from '../theme/ThemeContext';
import type {RootStackParamList} from '../navigation/AppNavigator';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {signup, AuthApiError} from '../api/authApi';
import {
  validateEmail,
  validateNickname,
  validatePassword,
  passwordRules,
  NICKNAME_MAX,
  PASSWORD_MAX,
} from '../utils/authValidation';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

type Field = 'name' | 'email' | 'password' | 'confirm';

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
  const [loading, setLoading] = useState(false);
  // 입력 도중부터 빨간 글씨가 뜨면 거슬리므로, 칸을 한 번 벗어났거나 가입을 눌렀을 때만 에러를 보인다.
  const [touched, setTouched] = useState<Record<Field, boolean>>({
    name: false, email: false, password: false, confirm: false,
  });
  const [submitted, setSubmitted] = useState(false);
  const [pwFocused, setPwFocused] = useState(false);
  // 중복 이메일처럼 서버만 알 수 있는 에러. 이메일을 고치면 지운다.
  const [emailServerError, setEmailServerError] = useState<string | null>(null);

  const nameRef = useRef<TextInput>(null);
  const emailRef = useRef<TextInput>(null);
  const passwordRef = useRef<TextInput>(null);
  const confirmRef = useRef<TextInput>(null);

  const errors: Record<Field, string | null> = {
    name: validateNickname(name),
    email: emailServerError ?? validateEmail(email),
    password: validatePassword(password),
    confirm: !passwordConfirm
      ? '비밀번호를 한 번 더 입력해주세요.'
      : password !== passwordConfirm
        ? '비밀번호가 일치하지 않습니다.'
        : null,
  };
  const visibleError = (f: Field) => ((touched[f] || submitted) ? errors[f] : null);
  // 확인 칸은 다 쳐보기 전에도 바로 일치 여부를 알려주는 편이 낫다.
  const confirmError = passwordConfirm.length > 0 ? errors.confirm : visibleError('confirm');
  const confirmOk = passwordConfirm.length > 0 && !errors.confirm;

  const rules = passwordRules(password);
  const showRules = pwFocused || password.length > 0 || touched.password || submitted;
  const pwRuleFailed = (touched.password || submitted) && rules.some(r => !r.ok);
  // 체크리스트로 안 보이는 에러(공백, 길이 초과)만 따로 글로 보여준다.
  const pwExtraError = password && rules.every(r => r.ok) ? visibleError('password') : null;

  const blur = (f: Field) => setTouched(t => ({...t, [f]: true}));

  const handleSignup = async () => {
    setSubmitted(true);
    const firstInvalid = (['name', 'email', 'password', 'confirm'] as Field[]).find(f => errors[f]);
    if (firstInvalid) {
      ({name: nameRef, email: emailRef, password: passwordRef, confirm: confirmRef})[firstInvalid]
        .current?.focus();
      return;
    }
    if (!agreed) {
      return;
    }
    setLoading(true);
    try {
      const {accessToken, refreshToken} =
        await signup(email.trim(), password, name.trim(), passwordConfirm);
      await AsyncStorage.multiSet([
        ['accessToken', accessToken],
        ['refreshToken', refreshToken],
      ]);
      // replace는 Signup만 걷어내고 그 아래(Login)를 남겨서, 뒤로가기로 로그인 화면에 도달할 수 있었다.
      // 거기서 "둘러보기"를 누르면 이미 가입·로그인된 상태로 온보딩을 건너뛴다. 스택을 통째로 비운다.
      navigation.reset({index: 0, routes: [{name: 'Onboarding'}]});
    } catch (e: any) {
      if (e instanceof AuthApiError && e.status === 409) {
        setEmailServerError(e.message);
        emailRef.current?.focus();
      } else if (e instanceof AuthApiError) {
        Alert.alert('회원가입 실패', e.message);
      } else {
        Alert.alert('회원가입 실패', '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setLoading(false);
    }
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
          <View>
            <View style={[styles.inputRow, visibleError('name') && styles.inputRowError]}>
              <MaterialIcons name="person-outline" size={20} color={colors.subText} style={styles.inputIcon} />
              <TextInput
                ref={nameRef}
                style={styles.input}
                placeholder="닉네임 (2~12자)"
                placeholderTextColor={colors.subText}
                value={name}
                onChangeText={setName}
                onBlur={() => blur('name')}
                maxLength={NICKNAME_MAX}
                autoCapitalize="none"
                autoCorrect={false}
                returnKeyType="next"
                onSubmitEditing={() => emailRef.current?.focus()}
                blurOnSubmit={false}
              />
            </View>
            <FieldMessage text={visibleError('name')} styles={styles} colors={colors} />
          </View>

          <View>
            <View style={[styles.inputRow, visibleError('email') && styles.inputRowError]}>
              <MaterialIcons name="mail-outline" size={20} color={colors.subText} style={styles.inputIcon} />
              <TextInput
                ref={emailRef}
                style={styles.input}
                placeholder="이메일"
                placeholderTextColor={colors.subText}
                value={email}
                onChangeText={v => {
                  setEmail(v);
                  setEmailServerError(null);
                }}
                onBlur={() => blur('email')}
                keyboardType="email-address"
                autoCapitalize="none"
                autoCorrect={false}
                autoComplete="email"
                textContentType="emailAddress"
                returnKeyType="next"
                onSubmitEditing={() => passwordRef.current?.focus()}
                blurOnSubmit={false}
              />
            </View>
            <FieldMessage text={visibleError('email')} styles={styles} colors={colors} />
          </View>

          <View>
            <View style={[styles.inputRow, (pwRuleFailed || pwExtraError) && styles.inputRowError]}>
              <MaterialIcons name="lock-outline" size={20} color={colors.subText} style={styles.inputIcon} />
              <TextInput
                ref={passwordRef}
                style={styles.input}
                placeholder="비밀번호 (영문, 숫자 포함 8자 이상)"
                placeholderTextColor={colors.subText}
                value={password}
                onChangeText={setPassword}
                onFocus={() => setPwFocused(true)}
                onBlur={() => {
                  setPwFocused(false);
                  blur('password');
                }}
                secureTextEntry={!showPw}
                maxLength={PASSWORD_MAX}
                autoCapitalize="none"
                autoCorrect={false}
                autoComplete="password-new"
                textContentType="newPassword"
                returnKeyType="next"
                onSubmitEditing={() => confirmRef.current?.focus()}
                blurOnSubmit={false}
              />
              <TouchableOpacity onPress={() => setShowPw(v => !v)} hitSlop={8}>
                <MaterialIcons name={showPw ? 'visibility' : 'visibility-off'} size={20} color={colors.subText} />
              </TouchableOpacity>
            </View>
            {showRules && (
              <View style={styles.rulesRow}>
                {rules.map(r => {
                  const color = r.ok ? colors.success : pwRuleFailed ? colors.danger : colors.subText;
                  return (
                    <View key={r.label} style={styles.rule}>
                      <MaterialIcons name={r.ok ? 'check' : 'close'} size={14} color={color} />
                      <Text style={[styles.ruleText, {color}]}>{r.label}</Text>
                    </View>
                  );
                })}
              </View>
            )}
            <FieldMessage text={pwExtraError} styles={styles} colors={colors} />
          </View>

          <View>
            <View style={[styles.inputRow, confirmError && styles.inputRowError]}>
              <MaterialIcons name="lock-outline" size={20} color={colors.subText} style={styles.inputIcon} />
              <TextInput
                ref={confirmRef}
                style={styles.input}
                placeholder="비밀번호 확인"
                placeholderTextColor={colors.subText}
                value={passwordConfirm}
                onChangeText={setPasswordConfirm}
                onBlur={() => blur('confirm')}
                secureTextEntry={!showPwConfirm}
                maxLength={PASSWORD_MAX}
                autoCapitalize="none"
                autoCorrect={false}
                autoComplete="password-new"
                textContentType="newPassword"
                returnKeyType="done"
                onSubmitEditing={handleSignup}
              />
              <TouchableOpacity onPress={() => setShowPwConfirm(v => !v)} hitSlop={8}>
                <MaterialIcons name={showPwConfirm ? 'visibility' : 'visibility-off'} size={20} color={colors.subText} />
              </TouchableOpacity>
            </View>
            {confirmOk ? (
              <FieldMessage text="비밀번호가 일치합니다." ok styles={styles} colors={colors} />
            ) : (
              <FieldMessage text={confirmError} styles={styles} colors={colors} />
            )}
          </View>

          <View>
            <TouchableOpacity style={styles.agreeRow} onPress={() => setAgreed(v => !v)} activeOpacity={0.7}>
              <MaterialIcons
                name={agreed ? 'check-circle' : 'radio-button-unchecked'}
                size={18}
                color={agreed ? colors.primary : submitted ? colors.danger : colors.subText}
              />
              <Text style={styles.agreeText}>
                <Text style={styles.agreeLink}>이용약관 및 개인정보 처리방침</Text>에 동의합니다 (필수)
              </Text>
            </TouchableOpacity>
            <FieldMessage
              text={submitted && !agreed ? '약관에 동의해야 가입할 수 있습니다.' : null}
              styles={styles}
              colors={colors}
            />
          </View>

          <TouchableOpacity
            style={[styles.signupBtn, loading && styles.btnDisabled]}
            onPress={handleSignup}
            disabled={loading}>
            {loading ? (
              <ActivityIndicator color="#FFF" />
            ) : (
              <Text style={styles.signupBtnText}>가입하기</Text>
            )}
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

function FieldMessage({
  text, ok = false, styles, colors,
}: {text: string | null; ok?: boolean; styles: ReturnType<typeof makeStyles>; colors: Colors}) {
  if (!text) {
    return null;
  }
  return (
    <View style={styles.messageRow}>
      <MaterialIcons
        name={ok ? 'check-circle-outline' : 'error-outline'}
        size={14}
        color={ok ? colors.success : colors.danger}
      />
      <Text style={[styles.messageText, {color: ok ? colors.success : colors.danger}]}>{text}</Text>
    </View>
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

    messageRow: {flexDirection: 'row', alignItems: 'center', gap: 4, marginLeft: 4, marginTop: 6},
    messageText: {flex: 1, fontSize: 12 * fs},
    rulesRow: {flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginLeft: 4, marginTop: 6},
    rule: {flexDirection: 'row', alignItems: 'center', gap: 2},
    ruleText: {fontSize: 12 * fs},

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
