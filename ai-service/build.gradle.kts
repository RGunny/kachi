plugins {
    kotlin("jvm")
    kotlin("plugin.spring")

    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "LLM 기반 키워드 확장, 뉴스 요약 서비스"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":ai-contract"))

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // Messaging
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Resilience
    implementation("io.github.resilience4j:resilience4j-reactor:2.3.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.3.0")

    // UUID v7
    implementation("com.github.f4b6a3:uuid-creator:5.3.7")

    // Test
    testImplementation("org.springframework.boot:spring-boot-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-data-mongodb-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")

    // TestContainers
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mongodb")
    testImplementation("org.testcontainers:testcontainers-kafka")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 실제 LLM을 부르는 테스트는 src/realTest에 둔다. test·check에 끼지 않는다.
// 실행: ./gradlew :ai-service:realTest [-Pkachi.llm.profile=local]
testing {
    suites {
        val realTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project())
                implementation(sourceSets.test.get().output)
            }
            targets.all {
                testTask.configure {
                    description = "설정된 후보 모델을 실제 제공자에 불러 확인한다."
                    systemProperty("kachi.llm.profile", providers.gradleProperty("kachi.llm.profile").getOrElse("local"))
                    outputs.upToDateWhen { false }
                    testLogging {
                        events("passed", "failed", "skipped")
                        // 모델·latency·토큰 보고가 stdout으로 나온다.
                        showStandardStreams = true
                    }
                }
            }
        }
    }
}

// 스위트는 main의 implementation 의존성을 물려받지 않는다. test와 같은 classpath로 맞춘다.
configurations {
    named("realTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("realTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
}
