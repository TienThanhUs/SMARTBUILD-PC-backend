package com.smartbuildpc.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    BUSINESS_RULE_VIOLATION(HttpStatus.BAD_REQUEST, "Vi phạm quy tắc nghiệp vụ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Xác thực không thành công hoặc không có quyền truy cập"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền thực hiện thao tác này"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên yêu cầu"),
    CONFLICT_STATE(HttpStatus.CONFLICT, "Xung đột trạng thái dữ liệu"),
    AI_BUILD_INFEASIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "Không thể tạo cấu hình PC phù hợp với yêu cầu"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Vượt quá giới hạn số lần yêu cầu"),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ AI tạm thời không khả dụng"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi máy chủ nội bộ");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
