plugins {
    java
    application
}

group = "me.rgunny.kachi.experiments"
version = "0.0.1"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val djlVersion = "0.37.0"

dependencies {
    implementation("ai.djl:api:$djlVersion")
    implementation("ai.djl.huggingface:tokenizers:$djlVersion")
    runtimeOnly("ai.djl.pytorch:pytorch-engine:$djlVersion")
    implementation("tools.jackson.core:jackson-databind:3.2.2")
}

application {
    mainClass.set("me.rgunny.kachi.experiments.goldset.Main")
    // 모델 두 개(bge-m3, bge-reranker-v2-m3)를 같은 JVM에 올린다. 텐서는 네이티브 메모리라 힙은 JSON 처리 몫이다.
    applicationDefaultJvmArgs = listOf("-Xmx4g")
}
