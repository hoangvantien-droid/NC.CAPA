package com.dqh.nccapa.common.error;

/** Mã lỗi nghiệp vụ (API §1.2). */
public enum ErrorCode {
    VALIDATION_ERROR,
    UNAUTHENTICATED,
    SCOPE_DENIED,
    ROLE_DENIED,
    GUARD_VIOLATION,
    IMMUTABLE_RECORD,
    NOT_FOUND,
    BUSINESS_RULE
}
