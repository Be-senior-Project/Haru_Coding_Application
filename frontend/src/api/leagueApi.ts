import {api} from './apiFetch';

export interface LeagueMember {
  userId: number;
  nickname: string;
  tier: string;
  score: number;
  rank: number;
  isMe: boolean;
}

export interface LeagueData {
  myTier: string;
  myRank: number;
  myScore: number;
  season: number;
  totalMembers: number;
  members: LeagueMember[];
}

export const leagueApi = {
  getMyLeague: () => api.get<LeagueData>('/api/leagues/my'),
};
