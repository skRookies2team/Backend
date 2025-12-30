-- Initialize default achievements
-- Achievement types: PLAY_COUNT, COMPLETION_COUNT, ENDING_COUNT, CREATION_COUNT, POST_COUNT

INSERT INTO achievements (code, name, description, type, target_value, icon_url, points) VALUES
-- Play Count Achievements
('FIRST_PLAY', '첫 발걸음', '첫 번째 스토리를 플레이하세요', 'PLAY_COUNT', 1, null, 10),
('CASUAL_PLAYER', '캐주얼 플레이어', '5개의 스토리를 플레이하세요', 'PLAY_COUNT', 5, null, 20),
('AVID_PLAYER', '열정적인 플레이어', '20개의 스토리를 플레이하세요', 'PLAY_COUNT', 20, null, 50),
('STORY_ADDICT', '스토리 중독자', '50개의 스토리를 플레이하세요', 'PLAY_COUNT', 50, null, 100),

-- Completion Count Achievements
('FIRST_COMPLETION', '완주자', '첫 번째 스토리를 완료하세요', 'COMPLETION_COUNT', 1, null, 15),
('STORY_FINISHER', '마무리의 달인', '10개의 스토리를 완료하세요', 'COMPLETION_COUNT', 10, null, 40),
('COMPLETION_MASTER', '완료 마스터', '30개의 스토리를 완료하세요', 'COMPLETION_COUNT', 30, null, 80),
('LEGENDARY_FINISHER', '전설의 완주자', '100개의 스토리를 완료하세요', 'COMPLETION_COUNT', 100, null, 200),

-- Ending Count Achievements
('ENDING_COLLECTOR', '엔딩 콜렉터', '5개의 다른 엔딩을 보세요', 'ENDING_COUNT', 5, null, 30),
('ENDING_HUNTER', '엔딩 헌터', '20개의 다른 엔딩을 보세요', 'ENDING_COUNT', 20, null, 60),
('ENDING_MASTER', '엔딩 마스터', '50개의 다른 엔딩을 보세요', 'ENDING_COUNT', 50, null, 120),
('ALL_ENDINGS', '모든 엔딩의 수집가', '100개의 다른 엔딩을 보세요', 'ENDING_COUNT', 100, null, 250),

-- Creation Count Achievements
('FIRST_CREATION', '창작의 시작', '첫 번째 스토리를 생성하세요', 'CREATION_COUNT', 1, null, 25),
('CREATIVE_MIND', '창의적인 작가', '5개의 스토리를 생성하세요', 'CREATION_COUNT', 5, null, 50),
('PROLIFIC_AUTHOR', '다작 작가', '15개의 스토리를 생성하세요', 'CREATION_COUNT', 15, null, 100),
('MASTER_CREATOR', '마스터 크리에이터', '30개의 스토리를 생성하세요', 'CREATION_COUNT', 30, null, 200),

-- Post Count Achievements
('FIRST_POST', '소통의 시작', '첫 번째 게시글을 작성하세요', 'POST_COUNT', 1, null, 5),
('ACTIVE_MEMBER', '활발한 회원', '10개의 게시글을 작성하세요', 'POST_COUNT', 10, null, 25),
('COMMUNITY_STAR', '커뮤니티 스타', '50개의 게시글을 작성하세요', 'POST_COUNT', 50, null, 75),
('LEGENDARY_CONTRIBUTOR', '전설의 기여자', '200개의 게시글을 작성하세요', 'POST_COUNT', 200, null, 150);
