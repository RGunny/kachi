package me.rgunny.kachi.notification.exception.routing

class RoutingJobConflictException(
    val eventKey: String,
    cause: Throwable? = null,
) : RoutingException(
    errorCode = RoutingErrorCode.ROUTING_JOB_CONFLICT,
    message = "routing job already exists. eventKey=$eventKey",
    cause = cause,
)
