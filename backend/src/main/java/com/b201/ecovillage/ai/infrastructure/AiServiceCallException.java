package com.b201.ecovillage.ai.infrastructure;

import java.util.List;

public class AiServiceCallException extends RuntimeException {

    private final String code;

    /**
     * AI 서비스가 돌려준 검증 코드다. 사유를 여기 담지 않으면 기록에도 교사 화면에도
     * "실패했다"만 남아 원인을 되짚을 수 없다.
     */
    private final List<String> validationCodes;

    public AiServiceCallException(
        String code,
        String message,
        List<String> validationCodes,
        Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.validationCodes = validationCodes == null ? List.of() : List.copyOf(validationCodes);
    }

    public AiServiceCallException(String code, String message, Throwable cause) {
        this(code, message, List.of(), cause);
    }

    public AiServiceCallException(String code, String message) {
        this(code, message, List.of(), null);
    }

    public String getCode() {
        return code;
    }

    public List<String> getValidationCodes() {
        return validationCodes;
    }
}
