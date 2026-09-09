package me.rgunny.kachi.story

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class StoryServiceApplication

fun main(args: Array<String>) {
    runApplication<StoryServiceApplication>(*args)
}
