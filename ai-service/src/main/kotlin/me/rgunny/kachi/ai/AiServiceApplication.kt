package me.rgunny.kachi.ai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AiServiceApplication

fun main(args: Array<String>) {
    runApplication<AiServiceApplication>(*args)
}
