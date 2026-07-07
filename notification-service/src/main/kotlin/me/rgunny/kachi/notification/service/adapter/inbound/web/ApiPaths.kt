package me.rgunny.kachi.notification.service.adapter.inbound.web

/**
 * notification-service HTTP API path contract.
 */
object ApiPaths {
    const val NOTIFICATIONS = "/notifications"
    const val ADMIN_NOTIFICATION_OUTBOXES = "/admin/notification-outboxes"
    const val ADMIN_NOTIFICATION_OUTBOX = "$ADMIN_NOTIFICATION_OUTBOXES/{outboxId}"
    const val ADMIN_NOTIFICATION_OUTBOX_RECOVER = "$ADMIN_NOTIFICATION_OUTBOX/recover"

    const val V1_NOTIFICATIONS = "${ApiVersions.V1_PATH_PREFIX}$NOTIFICATIONS"
    const val V1_ADMIN_NOTIFICATION_OUTBOXES = "${ApiVersions.V1_PATH_PREFIX}$ADMIN_NOTIFICATION_OUTBOXES"
    const val V1_ADMIN_NOTIFICATION_OUTBOX = "${ApiVersions.V1_PATH_PREFIX}$ADMIN_NOTIFICATION_OUTBOX"
    const val V1_ADMIN_NOTIFICATION_OUTBOX_RECOVER = "${ApiVersions.V1_PATH_PREFIX}$ADMIN_NOTIFICATION_OUTBOX_RECOVER"
}
