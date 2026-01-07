-- 성능 향상을 위한 인덱스 추가
-- 외래 키 컬럼은 이미 자동 인덱스가 생성되어 있으므로 제외
-- 멱등성 보장: 이미 존재하는 인덱스는 건너뜀

-- Like 테이블: user_id, target_type, target_id 복합 인덱스 (조회 성능 향상)
-- user_id는 FK이므로 이미 인덱스 있음, 복합 인덱스만 추가
DROP INDEX IF EXISTS idx_likes_user_target ON likes;
CREATE INDEX idx_likes_user_target ON likes(user_id, target_type, target_id);

-- Comment 테이블: post_id는 FK이므로 자동 인덱스 생성됨 - 생략

-- Post 테이블: user_id는 FK이므로 자동 인덱스 생성됨 - 생략

-- Bookmark 테이블: user_id, target_type, target_id 복합 인덱스
-- user_id는 FK이므로 이미 인덱스 있음, 복합 인덱스만 추가
DROP INDEX IF EXISTS idx_bookmarks_user_target ON bookmarks;
CREATE INDEX idx_bookmarks_user_target ON bookmarks(user_id, target_type, target_id);

-- StoryReview 테이블: story_data_id, user_id는 FK이므로 자동 인덱스 생성됨 - 생략
