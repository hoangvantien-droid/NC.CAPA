package com.dqh.nccapa.common.error;

/** Lỗi nghiệp vụ chung, mang ErrorCode + field (tuỳ chọn). */
public class BusinessException extends RuntimeException {
    private final ErrorCode code;
    private final String field;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public ErrorCode getCode() { return code; }
    public String getField() { return field; }

    public static BusinessException guard(String message) {
        return new BusinessException(ErrorCode.GUARD_VIOLATION, message);
    }
    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }
    public static BusinessException businessRule(String message, String field) {
        return new BusinessException(ErrorCode.BUSINESS_RULE, message, field);
    }
}
