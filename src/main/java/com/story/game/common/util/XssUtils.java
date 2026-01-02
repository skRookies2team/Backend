package com.story.game.common.util;

import lombok.experimental.UtilityClass;

/**
 * XSS(Cross-Site Scripting) 공격 방지를 위한 유틸리티 클래스
 */
@UtilityClass
public class XssUtils {

    /**
     * HTML 태그 및 특수문자를 이스케이프 처리
     * @param input 원본 텍스트
     * @return 이스케이프 처리된 텍스트
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }

    /**
     * 줄바꿈과 공백은 유지하면서 HTML 태그만 제거
     * @param input 원본 텍스트
     * @return HTML 태그가 제거된 텍스트
     */
    public static String stripHtmlTags(String input) {
        if (input == null) {
            return null;
        }

        // HTML 태그 제거 (간단한 패턴)
        String result = input.replaceAll("<[^>]*>", "");

        // 이스케이프된 HTML 엔티티 제거
        result = result.replaceAll("&lt;[^&gt;]*&gt;", "");

        return result;
    }

    /**
     * 스크립트 태그와 이벤트 핸들러 제거
     * @param input 원본 텍스트
     * @return 위험한 코드가 제거된 텍스트
     */
    public static String removeScripts(String input) {
        if (input == null) {
            return null;
        }

        String result = input;

        // <script> 태그 제거
        result = result.replaceAll("(?i)<script[^>]*>.*?</script>", "");

        // 이벤트 핸들러 제거 (onclick, onerror 등)
        result = result.replaceAll("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']", "");
        result = result.replaceAll("(?i)on\\w+\\s*=\\s*\\S+", "");

        // javascript: 프로토콜 제거
        result = result.replaceAll("(?i)javascript:", "");

        // <iframe> 태그 제거
        result = result.replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "");

        return result;
    }
}
