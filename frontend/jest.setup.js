/* global jest */
// 네이티브 모듈은 jest(Node)에서 동작하지 않으므로 각 라이브러리가 제공하는 mock으로 대체한다.
jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);
jest.mock('react-native-safe-area-context', () =>
  require('react-native-safe-area-context/jest/mock').default,
);
// google-signin은 package.json exports에 jest 경로를 열어두지 않아 절대 경로로 불러온다.
const path = require('path');
require(
  path.join(
    path.dirname(require.resolve('@react-native-google-signin/google-signin/package.json')),
    'jest/build/jest/setup.js',
  ),
);
