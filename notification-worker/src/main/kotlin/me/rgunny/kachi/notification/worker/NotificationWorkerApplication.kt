package me.rgunny.kachi.notification.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NotificationWorkerApplication

fun main(args: Array<String>) {
    runApplication<NotificationWorkerApplication>(*args)
}
