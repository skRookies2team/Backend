package com.story.game.common.exception;

/**
 * 중복된 리소스가 존재할 때 발생하는 예외
 * 예: 중복된 사용자명, 이메일 등
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }

    public DuplicateResourceException(String resourceType, String fieldName, String value) {
        super(String.format("%s already exists with %s: %s", resourceType, fieldName, value));
    }
}
