package com.story.game.common.exception;

/**
 * 잘못된 입력 데이터가 전달되었을 때 발생하는 예외
 * 예: 유효하지 않은 토큰, 잘못된 형식의 데이터 등
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidInputException(String fieldName, String reason) {
        super(String.format("Invalid input for %s: %s", fieldName, reason));
    }
}
