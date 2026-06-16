plugins {
    kotlin("jvm")
}

description = "알림 서비스 도메인 및 애플리케이션 코어 모듈"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // UUID v7
    implementation("com.github.f4b6a3:uuid-creator:5.3.7")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
