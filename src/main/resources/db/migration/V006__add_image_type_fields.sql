-- 이미지 타입 구분을 위한 필드 추가
-- Story 썸네일, Node 이미지 타입 추가

-- StoryData에 썸네일 필드 추가
ALTER TABLE story_data
ADD COLUMN thumbnail_url VARCHAR(500),
ADD COLUMN thumbnail_file_key VARCHAR(500);

-- StoryNode에 이미지 타입 필드 추가
ALTER TABLE story_nodes
ADD COLUMN image_type VARCHAR(50);

-- 기존 노드들의 이미지 타입을 depth에 따라 설정
-- depth=0이면 EPISODE_START, 그 외는 SCENE
UPDATE story_nodes
SET image_type = CASE
    WHEN depth = 0 THEN 'EPISODE_START'
    ELSE 'SCENE'
END
WHERE image_url IS NOT NULL;
