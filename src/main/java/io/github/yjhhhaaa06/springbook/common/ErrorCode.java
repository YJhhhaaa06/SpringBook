package io.github.yjhhhaaa06.springbook.common;

/**
 * 统一业务错误码与对应 HTTP 状态。
 * code 约定：400xx 参数/请求问题，401xx 未认证，403xx 禁止，404xx 资源不存在，409xx 业务冲突，500xx 系统错误。
 */
public enum ErrorCode {

    VALIDATION_FAILED(40001, 400, "参数校验失败"),

    UNAUTHORIZED(40100, 401, "未登录或凭证无效"),
    BAD_CREDENTIALS(40101, 401, "用户名或密码错误"),

    FORBIDDEN(40300, 403, "权限不足"),

    NOT_FOUND(40400, 404, "资源不存在"),
    BOOK_NOT_FOUND(40401, 404, "图书不存在"),
    READER_NOT_FOUND(40402, 404, "读者不存在"),
    LOAN_NOT_FOUND(40403, 404, "借阅记录不存在"),
    RESERVATION_NOT_FOUND(40404, 404, "预约不存在"),

    ISBN_DUPLICATED(40901, 409, "书号已存在"),
    BOOK_HAS_OPEN_LOANS(40902, 409, "图书存在未归还借阅，无法删除"),
    BOOK_HAS_ACTIVE_RESERVATIONS(40903, 409, "图书存在活跃预约，无法删除"),
    READER_PHONE_DUPLICATED(40904, 409, "手机号已存在"),
    READER_FROZEN(40905, 409, "读者已被冻结，无法借书"),
    BOOK_OUT_OF_STOCK(40906, 409, "可借数量不足"),
    LOAN_ALREADY_RETURNED(40907, 409, "该借阅已归还"),
    RESERVATION_NOT_ALLOWED(40908, 409, "当前有可借数量，无需预约"),
    DUPLICATE_ACTIVE_RESERVATION(40909, 409, "已有该书活跃预约，请勿重复预约"),
    RESERVATION_NOT_CANCELLABLE(40910, 409, "该预约状态不允许取消"),
    ACCOUNT_DISABLED(40911, 409, "账号已被禁用"),
    BOOK_VERSION_CONFLICT(40912, 409, "图书信息已被其他操作修改，请重试"),

    BOOK_STOCK_CONFLICT(50001, 500, "库存状态异常"),
    INTERNAL_ERROR(50000, 500, "系统内部错误");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
