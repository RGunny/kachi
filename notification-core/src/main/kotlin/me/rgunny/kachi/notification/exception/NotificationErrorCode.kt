package me.rgunny.kachi.notification.exception

import me.rgunny.kachi.notification.exception.ErrorCode

enum class NotificationErrorCode(
    override val code: String,
    override val message: String,
) : ErrorCode {
    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다"),
    INVALID_NOTIFICATION_STATE("INVALID_NOTIFICATION_STATE", "알림 상태가 올바르지 않습니다"),
    DISPATCH_NOT_READY("NOTIFICATION_DISPATCH_NOT_READY", "알림 발송 준비가 완료되지 않았습니다"),
    SENDER_NOT_FOUND("NOTIFICATION_SENDER_NOT_FOUND", "알림 발송 채널을 찾을 수 없습니다"),
    MULTIPLE_SENDERS_FOUND("MULTIPLE_NOTIFICATION_SENDERS_FOUND", "알림 발송 채널이 중복 등록되었습니다"),
    SEND_TRANSIENT_FAILURE("NOTIFICATION_SEND_TRANSIENT_FAILURE", "알림 발송 일시 실패"),
    SEND_PERMANENT_FAILURE("NOTIFICATION_SEND_PERMANENT_FAILURE", "알림 발송 영구 실패"),
    OUTBOX_PUBLISH_FAILURE("NOTIFICATION_OUTBOX_PUBLISH_FAILURE", "알림 발행 실패"),
    OUTBOX_NOT_FOUND("NOTIFICATION_OUTBOX_NOT_FOUND", "알림 outbox를 찾을 수 없습니다"),
    DLT_MESSAGE_NOT_FOUND("NOTIFICATION_DLT_MESSAGE_NOT_FOUND", "알림 DLT 메시지를 찾을 수 없습니다"),
    INVALID_DLT_MESSAGE_STATE("INVALID_NOTIFICATION_DLT_MESSAGE_STATE", "알림 DLT 메시지 상태가 올바르지 않습니다"),
}
