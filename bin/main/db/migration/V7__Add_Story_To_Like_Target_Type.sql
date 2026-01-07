-- Add STORY to target_type enum for likes table
ALTER TABLE likes MODIFY COLUMN target_type VARCHAR(20) NOT NULL;
