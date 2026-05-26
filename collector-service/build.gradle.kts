plugins {
    kotlin("jvm")
    kotlin("plugin.spring")

    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "뉴스, 시장 데이터 수집 서비스"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Resilience
    implementation("io.github.resilience4j:resilience4j-reactor:2.3.0")

    // UUID v7
    implementation("com.github.f4b6a3:uuid-creator:5.3.7")

    // Test
    testImplementation("org.springframework.boot:spring-boot-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-data-mongodb-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.projectreactor:reactor-test")

    // TestContainers
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mongodb")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
