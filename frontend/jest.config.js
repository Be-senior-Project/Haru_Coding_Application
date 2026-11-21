module.exports = {
  preset: '@react-native/jest-preset',
  // react-navigation 등 ES 모듈로 배포되는 라이브러리는 변환해야 jest가 읽을 수 있다
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?|@react-navigation|react-native-.*|@react-native-async-storage|@react-native-google-signin)/)',
  ],
  setupFiles: [
    './jest.setup.js',
    '<rootDir>/node_modules/@react-native-google-signin/google-signin/jest/build/jest/setup.js',
  ],
};
