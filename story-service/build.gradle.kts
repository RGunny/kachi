plugins {
    kotlin("jvm")
    kotlin("plugin.spring")

    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "기사를 같은 사건의 story로 묶는 조립 서비스"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Contract
    implementation(project(":collector-contract"))

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // Messaging
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Resilience
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.3.0")

    // UUID v7
    implementation("com.github.f4b6a3:uuid-creator:5.3.7")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-data-mongodb-test")
    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.junit.jupiter:junit-jupiter-params")

    // TestContainers
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mongodb")
    testImplementation("org.testcontainers:testcontainers-kafka")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 실제 추론 서버를 부르는 테스트는 src/realTest에 둔다. test·check에 끼지 않는다.
// 실행: ./scripts/infra.sh tei start && ./gradlew :story-service:realTest
testing {
    suites {
        val realTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project())
                implementation(sourceSets.test.get().output)
            }
            targets.all {
                testTask.configure {
                    description = "로컬 TEI 서버 둘을 실제로 불러 모델 정체와 골드셋 재현을 확인한다."
                    outputs.upToDateWhen { false }
                    testLogging {
                        events("passed", "failed", "skipped")
                        // 골드셋 대조·지연 보고가 stdout으로 나온다.
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
