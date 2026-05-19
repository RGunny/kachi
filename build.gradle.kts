plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    kotlin("plugin.jpa") version "2.2.21" apply false

    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

description = "kachi - 키워드 기반 뉴스 수집 및 AI 요약 서비스"

allprojects {
    group = "me.rgunny"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}