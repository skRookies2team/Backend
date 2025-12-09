-- 최종 엔딩 및 게이지 초기값 관련 DB 마이그레이션
-- 실행 전에 백업 필수!

-- 1. episode_endings 테이블에 ai_generated_id 컬럼 추가
ALTER TABLE episode_endings
ADD COLUMN ai_generated_id VARCHAR(255) AFTER id;

-- 2. 인덱스 추가 (검색 성능 향상)
CREATE INDEX idx_episode_endings_ai_id ON episode_endings(ai_generated_id);

-- 3. 기존 데이터 확인 (선택사항)
-- SELECT COUNT(*) FROM episode_endings WHERE ai_generated_id IS NULL;

-- 주의사항:
-- - 기존 데이터의 ai_generated_id는 NULL로 유지됩니다
-- - 새로 생성되는 엔딩부터 AI ID가 저장됩니다
-- - story_creation.ending_config_json 컬럼은 이미 존재하므로 추가 불필요
-- - gauges의 initial_value는 JSON 형태로 이미 저장되어 있으므로 컬럼 추가 불필요
