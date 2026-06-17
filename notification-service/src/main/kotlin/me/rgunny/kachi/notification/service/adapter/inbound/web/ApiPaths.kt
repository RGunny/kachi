package me.rgunny.kachi.notification.service.adapter.inbound.web

/**
 * notification-service HTTP API path contract.
 */
object ApiPaths {
    const val NOTIFICATIONS = "/notifications"

    const val V1_NOTIFICATIONS = "${ApiVersions.V1_PATH_PREFIX}$NOTIFICATIONS"
}
