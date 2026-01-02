package com.story.game.common.exception;

/**
 * 권한이 없는 리소스에 접근하려고 할 때 발생하는 예외
 * 예: 다른 사용자의 게시글 수정 시도 등
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }

    public ForbiddenException(String action, String resource) {
        super(String.format("You don't have permission to %s this %s", action, resource));
    }
}
