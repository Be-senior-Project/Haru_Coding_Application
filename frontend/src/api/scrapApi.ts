import {api} from './apiFetch';
import type {ProblemType} from '../types/problem';

/** 스크랩 목록 1건. 백엔드 ScrapResponse와 1:1. */
export interface Scrap {
  scrapId: number;
  problemId: number;
  title: string;
  category: string;
  subcategory?: string | null;
  difficulty: number; // 0 | 1 | 2
  language: string; // Python | Java | C++
  type: ProblemType;
  scrappedAt: string; // ISO
}

export const scrapApi = {
  /** 스크랩한 문제 목록(최신순). */
  list: () => api.get<Scrap[]>('/api/scraps'),

  /**
   * 스크랩 토글. 등록/해제를 서버가 알아서 뒤집는다.
   * @returns 토글 후 상태 (true = 스크랩됨)
   */
  toggle: (problemId: number) =>
    api
      .post<{scrapped: boolean}>(`/api/scraps/${problemId}`, {})
      .then(r => r.scrapped),

  /** 특정 문제의 스크랩 여부. 북마크 아이콘 초기 상태용. */
  isScrapped: (problemId: number) =>
    api.get<{scrapped: boolean}>(`/api/scraps/${problemId}`).then(r => r.scrapped),
};
