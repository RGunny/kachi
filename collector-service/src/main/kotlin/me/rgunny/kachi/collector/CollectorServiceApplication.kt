package me.rgunny.kachi.collector

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CollectorServiceApplication

fun main(args: Array<String>) {
    runApplication<CollectorServiceApplication>(*args)
}
