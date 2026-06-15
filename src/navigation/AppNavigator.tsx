import React from 'react';
import {NavigationContainer, DefaultTheme, DarkTheme} from '@react-navigation/native';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

import HomeScreen from '../screens/HomeScreen';
import StatsScreen from '../screens/StatsScreen';
import ProblemBankScreen from '../screens/ProblemBankScreen';
import ProfileScreen from '../screens/ProfileScreen';
import ProfileEditScreen from '../screens/ProfileEditScreen';
import ProblemSolveScreen from '../screens/ProblemSolveScreen';
import LoginScreen from '../screens/LoginScreen';
import SignupScreen from '../screens/SignupScreen';
import OnboardingScreen from '../screens/OnboardingScreen';
import OnboardingResultScreen from '../screens/OnboardingResultScreen';
import {useTheme} from '../theme/ThemeContext';
import type {Problem} from '../types/problem';

export type RootStackParamList = {
  Login: undefined;
  Signup: undefined;
  Onboarding: undefined;
  OnboardingResult: {difficulty: string; reason: string; focusPoint: string};
  Main: undefined;
  ProfileEdit: undefined;
  // problems: AI 생성문제 직접 전달 / problemId: 백엔드 실 문제 1개 / setId: 목 데모
  ProblemSolve: {setId?: string; problemId?: number; initialIndex?: number; problems?: Problem[]};
};

export type TabParamList = {
  홈: undefined;
  문제: undefined;
  '학습 통계': undefined;
  마이페이지: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<TabParamList>();

const TAB_ICON_NAMES: Record<string, string> = {
  홈: 'home',
  문제: 'layers',
  '학습 통계': 'bar-chart',
  마이페이지: 'person-outline',
};

function TabNavigator() {
  const {colors} = useTheme();
  return (
    <Tab.Navigator
      screenOptions={({route}) => ({
        tabBarIcon: ({focused, color}) => (
          <MaterialIcons name={TAB_ICON_NAMES[route.name]} size={focused ? 26 : 22} color={color} />
        ),
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: '#9E9E9E',
        tabBarStyle: {
          backgroundColor: colors.card,
          borderTopColor: colors.border,
          paddingBottom: 4,
          height: 60,
        },
        tabBarLabelStyle: {fontSize: 11},
        headerShown: false,
      })}>
      <Tab.Screen name="홈" component={HomeScreen} />
      <Tab.Screen name="문제" component={ProblemBankScreen} />
      <Tab.Screen name="학습 통계" component={StatsScreen} />
      <Tab.Screen name="마이페이지" component={ProfileScreen} />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  const {colors, isDark} = useTheme();

  const baseTheme = isDark ? DarkTheme : DefaultTheme;
  const navTheme = {
    ...baseTheme,
    colors: {
      ...baseTheme.colors,
      primary: colors.primary,
      background: colors.bg,
      card: colors.card,
      text: colors.text,
      border: colors.border,
      notification: colors.primary,
    },
  };

  return (
    <NavigationContainer theme={navTheme}>
      <Stack.Navigator screenOptions={{headerShown: false}} initialRouteName="Main">
        <Stack.Screen name="Login" component={LoginScreen} />
        <Stack.Screen name="Signup" component={SignupScreen} />
        <Stack.Screen name="Onboarding" component={OnboardingScreen} />
        <Stack.Screen name="OnboardingResult" component={OnboardingResultScreen} />
        <Stack.Screen name="Main" component={TabNavigator} />
        <Stack.Screen name="ProfileEdit" component={ProfileEditScreen} />
        <Stack.Screen name="ProblemSolve" component={ProblemSolveScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
