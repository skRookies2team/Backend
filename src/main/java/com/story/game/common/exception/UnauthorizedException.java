package com.story.game.common.exception;

/**
 * 인증되지 않았거나 권한이 없는 경우 발생하는 예외 (HTTP 403)
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
