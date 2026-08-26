import {api} from './apiFetch';

// 백엔드 TopicResponse (/api/topics)
export interface Topic {
  id: number;
  name: string;
  description?: string | null;
  icon?: string | null; // MaterialIcons 아이콘 이름 (seed: account-tree, data-object, code, assignment)
}

export const topicApi = {
  // 주제 목록 (공개)
  list: () => api.get<Topic[]>('/api/topics', false),
};
