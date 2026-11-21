// jest에서 네이티브 모듈 대신 쓸 목(mock). 각 라이브러리가 제공하는 jest용 목을 그대로 쓴다.
import {jest} from '@jest/globals';
import mockAsyncStorage from '@react-native-async-storage/async-storage/jest/async-storage-mock';
import mockSafeAreaContext from 'react-native-safe-area-context/jest/mock';

jest.mock('@react-native-async-storage/async-storage', () => mockAsyncStorage);
jest.mock('react-native-safe-area-context', () => mockSafeAreaContext);

// 테스트에서는 실제 서버로 요청하지 않는다(화면이 처음 그려질 때 보내는 요청이 연결을 기다리며 jest 종료를 늦춘다)
global.fetch = jest.fn(() => Promise.reject(new Error('테스트에서는 네트워크를 쓰지 않습니다')));
