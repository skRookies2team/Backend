package com.story.game.common.exception;

/**
 * 외부 서비스(AI 서버, RAG 서버 등) 호출 실패 시 발생하는 예외 (HTTP 502)
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
