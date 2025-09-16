package com.dauducbach.identity_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
@Getter

public enum ErrorCode {
    USER_NOT_FOUND(1000, "Người dùng không tồn tại", HttpStatus.NOT_FOUND),
    PASSWORD_INCORRECT(1001, "Mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(1002, "Token không hợp lệ", HttpStatus.UNAUTHORIZED),
    TOKEN_VERIFICATION_FAILED(1003, "Lỗi trong khi xác minh token", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_FAILED(1004, "Lỗi trong khi refresh token", HttpStatus.UNAUTHORIZED),
    AUTHENTICATION_FAILED(1005, "Xác thực thất bại", HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_LINKED(1006, "Email đã được liên kết với một tài khoản khác", HttpStatus.CONFLICT),
    USERNAME_EXISTS(1007, "Tên đăng nhập đã tồn tại", HttpStatus.CONFLICT),
    EMAIL_ALREADY_IN_USE(1008, "Email đã được liên kết với một tài khoản khác", HttpStatus.CONFLICT),
    CODE_CREATION_FAILED(1009, "Lỗi khi tạo code", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REGISTRATION_INFO(1010, "Vui lòng nhập lại thông tin đăng ký", HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_CODE(1011, "Mã xác thực không chính xác", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_LINKED(1012, "Email chưa được liên kết với tài khoản nào", HttpStatus.NOT_FOUND),
    TIMEOUT(1013, "Hết thời gian chờ, vui lòng nhập lại thông tin!", HttpStatus.REQUEST_TIMEOUT),
    INVALID_VERIFICATION(1014, "Mã xác minh không chính xác", HttpStatus.BAD_REQUEST),
    SEND_PASSWORD_FAILED(1015, "Lỗi trong quá trình gửi mật khẩu mới, vui lòng nhập lại thông tin.", HttpStatus.INTERNAL_SERVER_ERROR);
    ;
    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
