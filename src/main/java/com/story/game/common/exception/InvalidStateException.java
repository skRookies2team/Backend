package com.story.game.common.exception;

/**
 * 리소스가 작업을 수행하기에 적절하지 않은 상태인 경우 발생하는 예외 (HTTP 400)
 */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }

    public InvalidStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
