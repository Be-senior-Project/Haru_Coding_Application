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

// 테스트에서는 실제 서버로 요청하지 않는다(화면이 처음 그려질 때 보내는 요청이 연결을 기다리며 jest 종료를 늦춘다)
global.fetch = jest.fn(() => Promise.reject(new Error('테스트에서는 네트워크를 쓰지 않습니다')));
