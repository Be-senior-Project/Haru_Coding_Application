module.exports = {
  preset: '@react-native/jest-preset',
  // 기본값은 react-native, @react-native/* 만 변환한다.
  // react-native-*, @react-native-*/*, @react-navigation/* 도 ESM으로 배포되므로 변환 대상에 추가.
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native(-[^/]+)?|@react-native(-[^/]+)?|@react-navigation)/)',
  ],
  setupFiles: [
    require.resolve('@react-native/jest-preset/jest/setup.js'),
    '<rootDir>/jest.setup.js',
  ],
};
