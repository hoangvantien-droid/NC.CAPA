package com.dqh.nccapa.common.error;

/** Response wrapper (API §1.1). */
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorBody error;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message, String field) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.error = new ErrorBody(code, message, field);
        return r;
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public ErrorBody getError() { return error; }

    public static class ErrorBody {
        private final ErrorCode code;
        private final String message;
        private final String field;
        public ErrorBody(ErrorCode code, String message, String field) {
            this.code = code; this.message = message; this.field = field;
        }
        public ErrorCode getCode() { return code; }
        public String getMessage() { return message; }
        public String getField() { return field; }
    }
}
