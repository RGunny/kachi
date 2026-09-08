package me.rgunny.kachi.collector

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class CollectorServiceApplication

fun main(args: Array<String>) {
    runApplication<CollectorServiceApplication>(*args)
}
